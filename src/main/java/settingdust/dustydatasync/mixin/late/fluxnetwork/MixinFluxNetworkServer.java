package settingdust.dustydatasync.mixin.late.fluxnetwork;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FluxNetworksSyncer;
import settingdust.dustydatasync.ObservableCustomValue;
import sonar.fluxnetworks.api.network.NetworkMember;
import sonar.fluxnetworks.common.connection.FluxNetworkBase;
import sonar.fluxnetworks.common.connection.FluxNetworkServer;

import java.util.List;

@Mixin(value = FluxNetworkServer.class, remap = false)
public class MixinFluxNetworkServer extends FluxNetworkBase {
    @Inject(method = "addNewMember", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void dustydatasync$add(String name, CallbackInfo ci) {
        FluxNetworksSyncer.INSTANCE.emitUpdate(this, ((ObservableCustomValue<List<NetworkMember>>) network_players));
    }

    @ModifyExpressionValue(
        method = "removeMember",
        at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z")
    )
    private boolean dustydatasync$remove(final boolean original) {
        if (original)
            FluxNetworksSyncer.INSTANCE.emitUpdate(
                this,
                ((ObservableCustomValue<List<NetworkMember>>) network_players)
            );
        return original;
    }
}
