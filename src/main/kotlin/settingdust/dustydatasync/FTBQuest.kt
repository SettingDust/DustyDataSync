package settingdust.dustydatasync

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent
import com.feed_the_beast.ftblib.lib.data.Universe
import com.feed_the_beast.ftbquests.util.ServerQuestData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import settingdust.dustydatasync.mixin.ftbquests.ServerQuestDataAccessor
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

object FTBQuestTable : PlayerNbtTable()

class FTBQuestData(id: EntityID<UUID>) : PlayerNbtEntity(id, FTBQuestTable) {
    companion object : PlayerNbtEntityClass<FTBQuestData>(FTBQuestTable)
}

object FTBQuestSyncer {
    private val logger = LogManager.getLogger()
    @JvmStatic private val mutexs = mutableMapOf<UUID, Mutex>()

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLoadData(event: PlayerEvent.PlayerLoggedInEvent) =
        DustyDataSync.scope.launch {
            val forgePlayer = Universe.get().getPlayer(event.player)
            val uuid = forgePlayer.profile.id
            val mutex = mutexs.getOrPut(uuid) { Mutex() }
            mutex.lock()
            var retryCounter = 0
            newSuspendedTransaction {
                FTBQuestTable.insertIgnore {
                    it[id] = uuid
                    it[data] = NBTTagCompound()
                }
            }

            val uuidString = uuid.toString()
            val localLocked = uuidString in PlayerLocalLocker.players
            var databaseLocked = newSuspendedTransaction {
                FTBQuestTable.select(FTBQuestTable.lock)
                    .where { FTBQuestTable.id eq uuid }
                    .single()[FTBQuestTable.lock]
            }

            while (databaseLocked && !localLocked) {
                logger.debug(
                    "Waiting ${forgePlayer.name} unlock for ${DustyDataSync.Database.syncDelay}ms")
                delay(DustyDataSync.Database.syncDelay.milliseconds)
                if (retryCounter++ < RETRY_COUNT) {
                    logger.debug("Retried：$retryCounter")
                    databaseLocked = newSuspendedTransaction {
                        FTBQuestTable.select(FTBQuestTable.lock)
                            .where { FTBQuestTable.id eq uuid }
                            .single()[FTBQuestTable.lock]
                    }
                    continue
                }
                PlayerKicker.needKick += uuid
                logger.debug(
                    "Player ${forgePlayer.name} is locked in database and not locally locked on this server, enter is not allowed.")
                return@launch
            }

            val questData = ServerQuestData.get(forgePlayer.team)

            if (databaseLocked) {
                logger.warn(
                    "Player ${forgePlayer.name} is locked on this server, possibly due to an improper save upon quit; local data will need to override the database data.")
                newSuspendedTransaction {
                    FTBQuestTable.update({ FTBQuestTable.id eq uuid }) {
                        it[data] =
                            NBTTagCompound().also {
                                (questData as ServerQuestDataAccessor).`dustydatasync$writeData`(it)
                            }
                    }
                }
            } else {
                logger.debug("Player ${forgePlayer.name} is not locked, loading data.")
                newSuspendedTransaction {
                    logger.debug("Restoring data for ${forgePlayer.name}")
                    val tag =
                        FTBQuestTable.select(FTBQuestTable.data)
                            .where { FTBQuestTable.id eq uuid }
                            .single()[FTBQuestTable.data]
                    if (tag.isEmpty) {
                        logger.debug("Player ${forgePlayer.name} data is empty, storing data")
                        FTBQuestTable.update({ FTBQuestTable.id eq uuid }) {
                            it[data] =
                                NBTTagCompound().also { tag ->
                                    (questData as ServerQuestDataAccessor)
                                        .`dustydatasync$writeData`(tag)
                                }
                        }
                    } else {
                        logger.debug("Data：{}", tag)
                        (questData as ServerQuestDataAccessor).`dustydatasync$readData`(tag)
                        questData.markDirty()
                        // 非主线程访问会导致 https://github.com/vigna/fastutil/issues/42
                        DustyDataSync.serverCoroutineScope.launch {
                            ServerQuestData.onPlayerLoggedIn(ForgePlayerLoggedInEvent(forgePlayer))
                        }
                    }
                }
            }
            mutex.unlock()
        }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
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
                            FTBQuestTable.update({ FTBQuestTable.id eq uuid }) { it[lock] = true }
                        }
                    }
                }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player
        val uuid = player.uniqueID
        // 玩家不是在本地被锁定的，不能存数据进去
        if (uuid.toString() !in PlayerLocalLocker.players) return
        transaction {
            logger.debug("Player ${player.name} is exiting, storing data")

            FTBQuestTable.update({ FTBQuestTable.id eq uuid }) {
                it[lock] = false
                it[data] =
                    NBTTagCompound().also {
                        (ServerQuestData.get(Universe.get().getPlayer(player).team)
                                as ServerQuestDataAccessor)
                            .`dustydatasync$writeData`(it)
                    }
            }
        }
    }
}
