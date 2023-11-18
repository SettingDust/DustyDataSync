package settingdust.dustydatasync

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.relauncher.Side
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Duration.Companion.milliseconds

private const val RETRY_COUNT = 5

@EventBusSubscriber(value = [Side.SERVER], modid = DustyDataSync.MODID)
object DataSyncer {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.player
        if (player !is EntityPlayerMP) return
        val uuid = player.uniqueID
        var retryCounter = 0
        runBlocking {
            newSuspendedTransaction {
                PlayerDatas.insertIgnore {
                    it[id] = uuid
                    it[data] = NBTTagCompound()
                }
            }
            var playerData = newSuspendedTransaction { PlayerData[uuid] }
            val uuidString = uuid.toString()
            val localLocked = uuidString in Locks.players
            while (playerData.lock && !localLocked) {
                DustyDataSync.logger.debug("等待玩家解锁 ${DustyDataSync.Database.syncDelay} 毫秒")
                delay(DustyDataSync.Database.syncDelay.milliseconds)
                if (retryCounter++ < RETRY_COUNT) {
                    playerData = newSuspendedTransaction { PlayerData.reload(playerData)!! }
                    DustyDataSync.logger.debug("重试次数：$retryCounter")
                    continue
                }
                DustyDataSync.logger.debug("玩家被锁定且未在本服务器锁定，不允许进入")
                player.connection.disconnect(TextComponentString(DustyDataSync.Messages.kickLockMessage))
                return@runBlocking
            }

            newSuspendedTransaction {

                if (playerData.lock && localLocked) {
                    DustyDataSync.logger.warn("玩家在本服务器被锁定，可能是退出时没有正常保存，需要用本地数据覆盖数据库数据")

                    val dataTypes = DataType::class.sealedSubclasses.map { it.objectInstance!! }
                    for (dataType in dataTypes) {
                        val type = dataType::class.simpleName!!
                        DustyDataSync.logger.debug("存储 $type 数据")
                        val tag = playerData.data.getCompoundTag(type)
                        dataType.storeNBT(tag, uuid)
                    }

                    PlayerDatas.update({ PlayerDatas.id eq playerData.id }) {
                        it[data] = playerData.data
                    }
                } else {
                    DustyDataSync.logger.debug("玩家未锁定，加载数据")
                    Locks.players += uuidString
                    Locks.save()
                    playerData.lock = true

                    val dataTypes = DataType::class.sealedSubclasses.map { it.objectInstance!! }
                    for (dataType in dataTypes) {
                        val type = dataType::class.simpleName!!
                        DustyDataSync.logger.debug("恢复 $type 数据")
                        val tag = playerData.data.getCompoundTag(type)
                        playerData.data.setTag(type, tag)
                        if (tag.isEmpty) {
                            DustyDataSync.logger.debug("玩家数据库 $type 数据为空，存储数据")
                            dataType.storeNBT(tag, uuid)
                        } else dataType.restoreNBT(tag, uuid)
                    }

                    PlayerDatas.update({ PlayerDatas.id eq playerData.id }) {
                        it[data] = playerData.data
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player
        if (player !is EntityPlayerMP) return
        val uuid = player.uniqueID
        transaction {
            val playerData = PlayerData[uuid]

            val dataTypes = DataType::class.sealedSubclasses.map { it.objectInstance!! }

            for (dataType in dataTypes) {
                val type = dataType::class.simpleName!!
                DustyDataSync.logger.debug("存储 $type 数据")
                val tag = playerData.data.getCompoundTag(type)
                playerData.data.setTag(type, tag)
                dataType.storeNBT(tag, uuid)
            }

            Locks.players -= uuid.toString()
            Locks.save()

            PlayerDatas.update({ PlayerDatas.id eq playerData.id }) {
                it[data] = playerData.data
                it[lock] = false
            }
        }
    }
}
