package settingdust.dustydatasync.mixin.late.fluxnetwork;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import settingdust.dustydatasync.FluxNetworksSyncer;
import settingdust.dustydatasync.ObservableCustomValue;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.network.NetworkMember;
import sonar.fluxnetworks.common.connection.FluxNetworkBase;
import sonar.fluxnetworks.common.network.PacketGeneralHandler;

import java.util.List;

@Mixin(PacketGeneralHandler.class)
public class MixinPacketGeneralHandler {
    @Inject(
        method = "handleChangePermissionPacket",
        remap = false,
        at = @At(
            value = "INVOKE",
            ordinal = 1,
            target = "Lsonar/fluxnetworks/common/network/PacketNetworkUpdate$NetworkUpdateMessage;<init>(Ljava/util/List;Lsonar/fluxnetworks/api/utils/NBTType;)V"
        )
    )
    private static void dustydatasync$saveWhenChangePermission(
        final EntityPlayer player,
        final NBTTagCompound packetTag,
        final CallbackInfoReturnable<IMessage> cir,
        final @Local IFluxNetwork network
    ) {
        FluxNetworkBase base = (FluxNetworkBase) network;
        FluxNetworksSyncer.INSTANCE.emitUpdate(base, (ObservableCustomValue<List<NetworkMember>>) base.network_players);
    }
}
