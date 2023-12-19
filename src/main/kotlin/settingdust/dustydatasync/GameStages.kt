package settingdust.dustydatasync

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.darkhax.gamestages.data.IStageData
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

object GameStagesTable : PlayerNbtTable()

class GameStagesData(id: EntityID<UUID>) : PlayerNbtEntity(id, GameStagesTable) {
    companion object : PlayerNbtEntityClass<GameStagesData>(GameStagesTable)
}

@Mod.EventBusSubscriber(value = [Side.SERVER], modid = DustyDataSync.MODID)
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
                GameStagesTable.slice(GameStagesTable.lock)
                    .select { GameStagesTable.id eq uuid }
                    .single()[GameStagesTable.lock]
            }

            while (databaseLocked && !localLocked) {
                logger.debug("等待玩家 ${player.name} 解锁 ${DustyDataSync.Database.syncDelay} 毫秒")
                delay(DustyDataSync.Database.syncDelay.milliseconds)
                if (retryCounter++ < RETRY_COUNT) {
                    databaseLocked = newSuspendedTransaction {
                        GameStagesTable.slice(GameStagesTable.lock)
                            .select { GameStagesTable.id eq uuid }
                            .single()[GameStagesTable.lock]
                    }
                    logger.debug("重试次数：$retryCounter")
                    continue
                }
                logger.debug("玩家 ${player.name} 数据被锁定且未在本服务器锁定，不允许进入")
                PlayerKicker.needKick += uuid
                return@launch
            }

            if (databaseLocked) {
                logger.warn("玩家 ${player.name} 在本服务器被锁定，可能是退出时没有正常保存，需要用本地数据覆盖数据库数据")
                newSuspendedTransaction {
                    GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                        it[GameStagesTable.data] = stageData.writeToNBT()
                    }
                }
            } else {
                logger.debug("玩家 ${player.name} 未锁定，加载数据")
                newSuspendedTransaction {
                    logger.debug("恢复 ${player.name} 数据")
                    val tag =
                        GameStagesTable.slice(GameStagesTable.data)
                            .select { GameStagesTable.id eq uuid }
                            .single()[GameStagesTable.data]
                    if (tag.isEmpty) {
                        logger.debug("玩家 ${player.name} 数据为空，存储数据")
                        GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                            it[GameStagesTable.data] = stageData.writeToNBT()
                        }
                    } else {
                        stageData.clear()
                        logger.debug("数据：{}", tag)
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
                        logger.debug("锁定玩家 ${player.name}")
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
            logger.debug("保存玩家 ${player.name} 数据到文件，存储数据")
            GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                it[lock] = false
                it[data] = tag
            }
        }
    }
}
