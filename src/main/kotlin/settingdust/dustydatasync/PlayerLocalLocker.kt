package settingdust.dustydatasync

import kotlinx.coroutines.launch
import net.minecraft.entity.player.EntityPlayerMP
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager

@Mod.EventBusSubscriber(value = [Side.SERVER], modid = DustyDataSync.MODID)
object PlayerLocalLocker {
    var players: MutableSet<String>
        private set

    private var configFile: Path
    private val logger = LogManager.getLogger()

    init {
        val configDir = Loader.instance().configDir
        configFile = configDir.toPath() / "locks.txt"
        try {
            configFile.createParentDirectories()
            configFile.createFile()
        } catch (_: Throwable) {}
        players = configFile.readLines().toMutableSet()
    }

    fun save() = configFile.writeLines(players)

    // 最后本地锁定玩家
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        DustyDataSync.scope.launch ignored@{
            DustyDataSync.serverCoroutineScope.launch {
                if (!player.connection.networkManager.isChannelOpen) return@launch
                logger.debug("本地锁定玩家 ${player.name}")
                players += uuid.toString()
                save()
            }
        }
    }

    // 在所有东西都保存后本地解锁玩家
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        DustyDataSync.scope.launch {
            DustyDataSync.serverCoroutineScope.launch {
                logger.debug("本地解锁玩家 ${player.name}")
                players -= uuid.toString()
                save()
            }
        }
    }
}
