package settingdust.dustydatasync.mixin.late.ftbquests;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamLoadedEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamSavedEvent;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FTBQuestSyncer;

import java.io.File;

@Mixin(value = ServerQuestData.class, remap = false)
public class MixinServerQuestData {
    @WrapMethod(method = "onPlayerLoggedIn")
    private static void dustydatasync$avoidOffline(
        final ForgePlayerLoggedInEvent event,
        final Operation<Void> original
    ) {
        if (!event.getPlayer().isOnline()) return;
        original.call(event);
    }

    @Inject(method = "onTeamSaved", at = @At(value = "RETURN"))
    private static void dustydatasync$saveTeam(
        final ForgeTeamSavedEvent event,
        final CallbackInfo ci,
        @Local NBTTagCompound nbt
    ) {
        FTBQuestSyncer.INSTANCE.save(event.getTeam(), nbt);
    }


    @WrapOperation(
        method = "onTeamLoaded",
        at = @At(
            value = "INVOKE",
            target = "Lcom/feed_the_beast/ftblib/lib/util/NBTUtils;readNBT(Ljava/io/File;)Lnet/minecraft/nbt/NBTTagCompound;"
        )
    )
    private static NBTTagCompound dustydatasync$loadTeam(
        final File file,
        Operation<NBTTagCompound> original,
        ForgeTeamLoadedEvent event
    ) {
        return FTBQuestSyncer.INSTANCE.load(event.getTeam(), () -> original.call(file));
    }
}
