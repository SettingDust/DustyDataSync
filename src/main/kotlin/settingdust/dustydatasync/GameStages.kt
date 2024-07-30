package settingdust.dustydatasync

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.darkhax.gamestages.GameStages
import net.darkhax.gamestages.data.IStageData
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

object GameStagesTable : PlayerNbtTable()

class GameStagesData(id: EntityID<UUID>) : PlayerNbtEntity(id, GameStagesTable) {
    companion object : PlayerNbtEntityClass<GameStagesData>(GameStagesTable)
}

object GameStagesSyncer {
    @JvmStatic private val logger = LogManager.getLogger()
    @JvmStatic private val mutexs = mutableMapOf<UUID, Mutex>()

    @JvmStatic
    fun onLoadData(event: PlayerEvent.LoadFromFile, stageData: IStageData) =
        DustyDataSync.scope.launch {
            val player = event.entityPlayer as EntityPlayerMP
            val uuid = player.uniqueID
            val mutex = mutexs.getOrPut(uuid) { Mutex() }
            mutex.lock()
            var retryCounter = 0
            newSuspendedTransaction {
                GameStagesTable.insertIgnore {
                    it[id] = uuid
                    it[data] = NBTTagCompound()
                }
            }
            val uuidString = uuid.toString()
            val localLocked = uuidString in PlayerLocalLocker.players
            var databaseLocked = newSuspendedTransaction {
                GameStagesTable.select(GameStagesTable.lock)
                    .where { GameStagesTable.id eq uuid }
                    .single()[GameStagesTable.lock]
            }

            while (databaseLocked && !localLocked) {
                logger.debug(
                    "Waiting unlocking ${player.name} for ${DustyDataSync.Database.syncDelay}ms")
                delay(DustyDataSync.Database.syncDelay.milliseconds)
                if (retryCounter++ < RETRY_COUNT) {
                    databaseLocked = newSuspendedTransaction {
                        GameStagesTable.select(GameStagesTable.lock)
                            .where { GameStagesTable.id eq uuid }
                            .single()[GameStagesTable.lock]
                    }
                    logger.debug("Retried：$retryCounter")
                    continue
                }
                logger.debug(
                    "Player ${player.name} is locked in database and not locally locked on this server, enter is not allowed.")
                PlayerKicker.needKick += uuid
                return@launch
            }

            if (databaseLocked) {
                logger.warn(
                    "Player ${player.name} is locked on this server, possibly due to an improper save upon quit; local data will need to override the database data.")

                newSuspendedTransaction {
                    GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                        it[data] = stageData.writeToNBT()
                    }
                }
            } else {
                logger.debug("Player ${player.name} is not locked, loading data.")
                newSuspendedTransaction {
                    logger.debug("Restoring data for ${player.name}")
                    val tag =
                        GameStagesTable.select(GameStagesTable.data)
                            .where { GameStagesTable.id eq uuid }
                            .single()[GameStagesTable.data]
                    if (tag.isEmpty) {
                        logger.debug("Player ${player.name} data is empty, storing data")
                        GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                            it[data] = stageData.writeToNBT()
                        }
                    } else {
                        stageData.clear()
                        logger.debug("Data：{}", tag)
                        stageData.readFromNBT(tag)
                    }
                }
            }
            mutex.unlock()
        }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerLogin(event: PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        DustyDataSync.scope.launch {
            mutexs
                .getOrPut(uuid) { Mutex() }
                .withLock {
                    if (player.connection.networkManager.isChannelOpen) {
                        // 等待其他数据判断完毕，没有踢出之后再锁定玩家
                        logger.debug("Locking player ${player.name}")
                        transaction {
                            GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                                it[lock] = true
                            }
                        }
                    }
                }
        }
    }

    @JvmStatic
    fun onSaveData(event: PlayerEvent.SaveToFile, tag: NBTTagCompound) {
        val player = event.entityPlayer
        val uuid = player.uniqueID
        // 玩家不是在本地被锁定的，不能存数据进去
        if (uuid.toString() !in PlayerLocalLocker.players) return
        transaction {
            logger.debug("Saving player ${player.name} data to file")
            GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                it[lock] = false
                it[data] = tag
            }
        }
    }
}
