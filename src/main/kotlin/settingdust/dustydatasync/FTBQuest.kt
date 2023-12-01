package settingdust.dustydatasync

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent
import com.feed_the_beast.ftblib.lib.data.Universe
import com.feed_the_beast.ftbquests.util.ServerQuestData
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import settingdust.dustydatasync.mixin.ftbquests.ServerQuestDataAccessor

object FTBQuestTable : PlayerNbtTable()

class FTBQuestData(id: EntityID<UUID>) : PlayerNbtEntity(id, FTBQuestTable) {
    companion object : PlayerNbtEntityClass<FTBQuestData>(FTBQuestTable)
}

@Mod.EventBusSubscriber(value = [Side.SERVER], modid = DustyDataSync.MODID)
object FTBQuestSyncer {
    private val logger = LogManager.getLogger()

    @JvmStatic
    fun onLoadData(event: ForgePlayerLoggedInEvent, questData: ServerQuestData) = runBlocking {
        val forgePlayer = event.team.owner
        val uuid = forgePlayer.profile.id
        var retryCounter = 0
        newSuspendedTransaction {
            FTBQuestTable.insertIgnore {
                it[id] = uuid
                it[data] = NBTTagCompound()
            }
        }

        val uuidString = uuid.toString()
        val localLocked = uuidString in Locks.players
        val databaseLocked = suspend {
            newSuspendedTransaction {
                FTBQuestTable.slice(FTBQuestTable.lock)
                    .select { FTBQuestTable.id eq uuid }
                    .single()[FTBQuestTable.lock]
            }
        }
        while (databaseLocked() && !localLocked) {
            logger.debug("等待玩家 ${forgePlayer.name} 解锁 ${DustyDataSync.Database.syncDelay} 毫秒")
            delay(DustyDataSync.Database.syncDelay.milliseconds)
            if (retryCounter++ < RETRY_COUNT) {
                logger.debug("重试次数：$retryCounter")
                continue
            }
            logger.debug("玩家 ${forgePlayer.name} 数据被锁定且未在本服务器锁定，不允许进入")
            return@runBlocking
        }

        val playerData = newSuspendedTransaction { FTBQuestData[uuid] }

        if (playerData.lock && localLocked) {
            logger.warn("玩家 ${forgePlayer.name} 在本服务器被锁定，可能是退出时没有正常保存，需要用本地数据覆盖数据库数据")
            newSuspendedTransaction {
                playerData.data =
                    NBTTagCompound().also {
                        (questData as ServerQuestDataAccessor).`dustydatasync$writeData`(it)
                    }
            }
        } else {
            logger.debug("玩家 ${forgePlayer.name} 未锁定，加载数据")
            newSuspendedTransaction {
                logger.debug("恢复 ${forgePlayer.name} 数据")
                val tag = playerData.data
                if (tag.isEmpty) {
                    logger.debug("玩家 ${forgePlayer.name} 数据为空，存储数据")
                    playerData.data =
                        NBTTagCompound().also {
                            (questData as ServerQuestDataAccessor).`dustydatasync$writeData`(it)
                        }
                } else {
                    questData.markDirty()
                    (questData as ServerQuestDataAccessor).`dustydatasync$readData`(playerData.data)
                }
            }
        }
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        DustyDataSync.scope.launch {
            player.server.addScheduledTask {
                if (player.connection.networkManager.isChannelOpen) {
                    val uuid = player.uniqueID
                    // 等待其他数据判断完毕，没有踢出之后再锁定玩家
                    logger.debug("锁定玩家 ${player.name}")
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
        if (uuid.toString() !in Locks.players) return
        transaction {
            logger.debug("玩家 ${player.name} 退出，存储数据")

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
