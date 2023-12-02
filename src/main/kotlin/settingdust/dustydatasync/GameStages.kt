package settingdust.dustydatasync

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.darkhax.gamestages.data.IStageData
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.Mod
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

    @JvmStatic private val waitForKick = mutableSetOf<EntityPlayerMP>()

    @JvmStatic
    fun onLoadData(event: PlayerEvent.LoadFromFile, stageData: IStageData) =
        DustyDataSync.scope.launch {
            val player = event.entityPlayer as EntityPlayerMP
            val uuid = player.uniqueID
            var retryCounter = 0
            newSuspendedTransaction {
                GameStagesTable.insertIgnore {
                    it[id] = uuid
                    it[data] = NBTTagCompound()
                }
            }
            val uuidString = uuid.toString()
            val localLocked = uuidString in Locks.players
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
                waitForKick += player
                return@launch
            }

            val playerData = newSuspendedTransaction { GameStagesData[uuid] }

            if (playerData.lock && localLocked) {
                logger.warn("玩家 ${player.name} 在本服务器被锁定，可能是退出时没有正常保存，需要用本地数据覆盖数据库数据")
                newSuspendedTransaction { playerData.data = stageData.writeToNBT() }
            } else {
                logger.debug("玩家 ${player.name} 未锁定，加载数据")
                newSuspendedTransaction {
                    logger.debug("恢复 ${player.name} 数据")
                    val tag = playerData.data
                    if (tag.isEmpty) {
                        logger.debug("玩家 ${player.name} 数据为空，存储数据")
                        newSuspendedTransaction { playerData.data = stageData.writeToNBT() }
                    } else {
                        stageData.clear()
                        stageData.readFromNBT(playerData.data)
                    }
                }
            }
        }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        DustyDataSync.serverCoroutineScope.launch {
            if (player.connection.networkManager.isChannelOpen) {
                val uuid = player.uniqueID
                // 等待其他数据判断完毕，没有踢出之后再锁定玩家
                logger.debug("锁定玩家 ${player.name}")
                transaction {
                    GameStagesTable.update({ GameStagesTable.id eq uuid }) { it[lock] = true }
                }

                if (player !in waitForKick) return@launch
                player.connection.disconnect(
                    TextComponentString(DustyDataSync.Messages.kickLockMessage)
                )
                waitForKick.remove(player)
            }
        }
    }

    @JvmStatic
    fun onSaveData(event: PlayerEvent.SaveToFile, tag: NBTTagCompound) {
        val player = event.entityPlayer
        val uuid = player.uniqueID
        // 玩家不是在本地被锁定的，不能存数据进去
        if (uuid.toString() !in Locks.players) return
        transaction {
            logger.debug("保存玩家 ${player.name} 数据到文件，存储数据")
            GameStagesTable.update({ GameStagesTable.id eq uuid }) {
                it[lock] = false
                it[data] = tag
            }
        }
    }
}
