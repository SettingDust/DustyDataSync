package settingdust.dustydatasync.mixin.fluxnetwork;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import settingdust.dustydatasync.FluxNetworksSyncer;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkCache;

import java.util.Collection;

@Mixin(value = FluxNetworkCache.class, remap = false)
public class MixinFluxNetworkCache {
    @Inject(method = "getNetwork", at = @At(value = "HEAD"))
    private void dustydatasync$loadData(int id, CallbackInfoReturnable<IFluxNetwork> cir) {
        FluxNetworksSyncer.loadNetwork(id);
    }

    @Inject(method = "getAllNetworks", at = @At(value = "HEAD"))
    private void dustydatasync$loadData(CallbackInfoReturnable<Collection<IFluxNetwork>> cir) {
        FluxNetworksSyncer.onLoadData();
    }
}
