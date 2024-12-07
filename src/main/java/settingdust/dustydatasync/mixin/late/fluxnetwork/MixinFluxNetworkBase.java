package settingdust.dustydatasync.mixin.late.fluxnetwork;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FluxNetworksSyncer;
import settingdust.dustydatasync.ObservableCustomValue;
import sonar.fluxnetworks.api.network.NetworkMember;
import sonar.fluxnetworks.api.network.SecurityType;
import sonar.fluxnetworks.api.utils.EnergyType;
import sonar.fluxnetworks.api.utils.ICustomValue;
import sonar.fluxnetworks.common.connection.FluxNetworkBase;
import sonar.fluxnetworks.common.connection.NetworkStatistics;

import java.util.List;
import java.util.UUID;

@Mixin(value = FluxNetworkBase.class, remap = false)
public class MixinFluxNetworkBase {
    @Shadow public ICustomValue<String> network_name;
    @Shadow public ICustomValue<UUID> network_owner;
    @Shadow public ICustomValue<SecurityType> network_security;
    @Shadow public ICustomValue<String> network_password;
    @Shadow public ICustomValue<Integer> network_color;
    @Shadow public ICustomValue<EnergyType> network_energy;
    @Shadow public ICustomValue<Integer> network_wireless;
    @Shadow public ICustomValue<NetworkStatistics> network_stats;
    @Shadow public ICustomValue<List<NetworkMember>> network_players;

    @Inject(method = "<init>()V", at = @At("TAIL"))
    private void dustydatasync$init(CallbackInfo ci) {
        network_name = new ObservableCustomValue<>(network_name);
        network_owner = new ObservableCustomValue<>(network_owner);
        network_security = new ObservableCustomValue<>(network_security);
        network_password = new ObservableCustomValue<>(network_password);
        network_color = new ObservableCustomValue<>(network_color);
        network_energy = new ObservableCustomValue<>(network_energy);
        network_wireless = new ObservableCustomValue<>(network_wireless);
        network_stats = new ObservableCustomValue<>(network_stats);
        network_players = new ObservableCustomValue<>(network_players);

        FluxNetworksSyncer.INSTANCE.observeValues((FluxNetworkBase) (Object) this);
    }
}
