package settingdust.dustydatasync

import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import java.util.*

@Mod.EventBusSubscriber(value = [Side.SERVER], modid = DustyDataSync.MODID)
object PlayerKicker {
    val needKick = mutableSetOf<UUID>()

    @JvmStatic private val logger = LogManager.getLogger()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        if (uuid in needKick) {
            logger.debug("踢出玩家 {}", player.name)
            player.connection.disconnect(
                TextComponentString(DustyDataSync.Messages.kickLockMessage)
            )
            needKick.remove(uuid)
        }
    }
}
