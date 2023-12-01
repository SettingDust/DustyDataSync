package settingdust.dustydatasync.mixin.fluxnetwork;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FluxNetworksSyncer;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkServer;

import java.util.UUID;

@Mixin(value = FluxNetworkServer.class, remap = false)
public class MixinFluxNetworkServer {
    @Inject(method = "addNewMember", at = @At(value = "TAIL"))
    private void dustydatasync$add(String name, CallbackInfo ci) {
        FluxNetworksSyncer.onModify((IFluxNetwork) this);
    }

    @Inject(method = "removeMember", at = @At(value = "HEAD"))
    private void dustydatasync$remove(UUID uuid, CallbackInfo ci) {
        FluxNetworksSyncer.onModify((IFluxNetwork) this);
    }
}
