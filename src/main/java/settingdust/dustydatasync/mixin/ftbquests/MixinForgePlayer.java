package settingdust.dustydatasync.mixin.ftbquests;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.Universe;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgePlayer.class, remap = false)
public abstract class MixinForgePlayer {
    @Shadow
    public abstract boolean isOnline();

    @Inject(method = "onLoggedIn", at = @At(value = "HEAD"), cancellable = true)
    private void dustydatasync$avoidOffline(
            EntityPlayerMP player, Universe universe, boolean firstLogin, CallbackInfo ci) {
        if (!isOnline()) {
            ci.cancel();
        }
    }
}
