package settingdust.dustydatasync.mixin.ftbquests;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FTBQuestSyncer;

@Mixin(value = ServerQuestData.class, remap = false)
public class MixinServerQuestData {
    @Inject(method = "onPlayerLoggedIn", at = @At(value = "HEAD"), cancellable = true)
    private static void dustydatasync$avoidOffline(ForgePlayerLoggedInEvent event, CallbackInfo ci) {
        if (!event.getPlayer().isOnline()) {
            ci.cancel();
        }
    }
}
