package settingdust.dustydatasync.mixin.late.fluxnetwork;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FluxNetworksSyncer;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.common.data.FluxNetworkData;

@Mixin(value = FluxNetworkData.class, remap = false)
public class MixinFluxNetworkData {
    @Shadow private static FluxNetworkData data;

    @Inject(
        method = "loadData",
        at = @At(value = "INVOKE", ordinal = 2, target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V")
    )
    private static void dustydatasync$load(CallbackInfo ci) {
        FluxNetworksSyncer.INSTANCE.loadNetworks(data);
    }

    @Inject(method = "addNetwork", at = @At(value = "TAIL"))
    private void dustydatasync$add(IFluxNetwork network, CallbackInfo ci) {
        FluxNetworksSyncer.INSTANCE.addNetwork(network);
    }

    @Inject(method = "removeNetwork", at = @At(value = "HEAD"))
    private void dustydatasync$remove(IFluxNetwork network, CallbackInfo ci) {
        FluxNetworksSyncer.INSTANCE.removeNetwork(network);
    }
}
