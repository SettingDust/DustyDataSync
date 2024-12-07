package settingdust.dustydatasync.mixin.late.ftblib;

import com.feed_the_beast.ftblib.lib.data.Universe;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.NBTTagCompound;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.FTBLibSyncer;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Mixin(value = Universe.class, remap = false)
public abstract class MixinUniverse {

    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            ordinal = 0,
            target = "Lcom/feed_the_beast/ftblib/lib/util/NBTUtils;readNBT(Ljava/io/File;)Lnet/minecraft/nbt/NBTTagCompound;"
        )
    )
    private NBTTagCompound dustydatasync$loadUniverseFromDatabase(final File instance) {
        return FTBLibSyncer.INSTANCE.loadUniverse((Universe) (Object) this);
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/File;listFiles()[Ljava/io/File;"))
    private File[] dustydatasync$cancelOriginalLoad(
        final File instance
    ) {
        return null;
    }

    @Inject(
        method = "load",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lcom/feed_the_beast/ftblib/lib/data/Universe;fakePlayerTeam:Lcom/feed_the_beast/ftblib/lib/data/ForgeTeam;"
        )
    )
    private void dustydatasync$loadFromDatabase(
        final CallbackInfo ci,
        @Local(ordinal = 0) Map<UUID, NBTTagCompound> playerNBT,
        @Local(ordinal = 1) Map<String, NBTTagCompound> teamNBT
    ) {
        FTBLibSyncer.INSTANCE.loadPlayers((Universe) (Object) this, playerNBT);
        FTBLibSyncer.INSTANCE.loadTeams((Universe) (Object) this, teamNBT);
    }

    @Redirect(
        method = "save",
        at = @At(
            value = "INVOKE",
            ordinal = 0,
            target = "Lcom/feed_the_beast/ftblib/lib/util/NBTUtils;writeNBTSafe(Ljava/io/File;Lnet/minecraft/nbt/NBTTagCompound;)V"
        )
    )
    private void dustydatasync$saveUniverseToDatabase(final File instance, final NBTTagCompound nbt) {
        FTBLibSyncer.INSTANCE.saveUniverse((Universe) (Object) this, nbt);
    }

    @Redirect(method = "save", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<?> dustydatasync$cancelOriginalPlayerSave(final Map instance) {return Collections.emptySet();}

    @Inject(
        method = "save",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lcom/feed_the_beast/ftblib/lib/data/Universe;checkSaving:Z"
        )
    )
    private void dustydatasync$savePlayers(final CallbackInfo ci) {
        FTBLibSyncer.INSTANCE.savePlayers((Universe) (Object) this);
    }

    @Redirect(
        method = "save",
        at = @At(
            value = "INVOKE",
            target = "Lcom/feed_the_beast/ftblib/lib/data/Universe;getTeams()Ljava/util/Collection;"
        )
    )
    private Collection<?> dustydatasync$cancelOriginalTeamSave(final Universe instance) {return Collections.emptySet();}

    @Inject(
        method = "save",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lcom/feed_the_beast/ftblib/lib/data/Universe;checkSaving:Z"
        )
    )
    private void dustydatasync$saveTeams(final CallbackInfo ci) {
        FTBLibSyncer.INSTANCE.saveTeams((Universe) (Object) this);
    }
}
