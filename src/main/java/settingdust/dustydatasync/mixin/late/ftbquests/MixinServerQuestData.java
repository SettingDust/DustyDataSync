package settingdust.dustydatasync.mixin.late.ftbquests;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerQuestData.class, remap = false)
public class MixinServerQuestData {
    @Inject(method = "onPlayerLoggedIn", at = @At(value = "HEAD"), cancellable = true)
    private static void dustydatasync$avoidOffline(ForgePlayerLoggedInEvent event, CallbackInfo ci) {
        if (!event.getPlayer().isOnline()) {
            ci.cancel();
        }
    }
}
