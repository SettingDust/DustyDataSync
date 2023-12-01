package settingdust.dustydatasync.mixin.fluxnetwork;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FluxNetworksSyncer;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.network.NetworkSettings;
import sonar.fluxnetworks.common.connection.FluxNetworkBase;

@Mixin(value = FluxNetworkBase.class, remap = false)
public class MixinFluxNetworkBase<T> {
    @Inject(method = "setSetting", at = @At(value = "TAIL"))
    private void dustydatasync$onModify(NetworkSettings<T> settings, T value, CallbackInfo ci) {
        if (settings == NetworkSettings.NETWORK_ID || settings == NetworkSettings.ALL_CONNECTORS) return;
        FluxNetworksSyncer.onModify((IFluxNetwork) this);
    }
}
