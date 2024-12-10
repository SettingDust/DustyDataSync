package settingdust.dustydatasync.mixin.late.gamestages;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.darkhax.gamestages.data.GameStageSaveHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import settingdust.dustydatasync.GameStagesSyncer;

import java.io.File;

@Mixin(value = GameStageSaveHandler.class)
public abstract class MixinGameStageSaveHandler {

    @WrapOperation(
        method = "onPlayerLoad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompressedStreamTools;read(Ljava/io/File;)Lnet/minecraft/nbt/NBTTagCompound;"
        )
    )
    private static NBTTagCompound dustydatasync$loadFromDatabase(final File file, Operation<NBTTagCompound> original, PlayerEvent.LoadFromFile event) {
        return GameStagesSyncer.INSTANCE.getPlayerData(event.getPlayerUUID(), () -> original.call(file));
    }

    @ModifyArg(
        method = "onPlayerSave",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompressedStreamTools;write(Lnet/minecraft/nbt/NBTTagCompound;Ljava/io/File;)V"
        )
    )
    private static NBTTagCompound dustydatasync$saveToDatabase(
        final NBTTagCompound compound,
        @Local PlayerEvent.SaveToFile event
    ) {
        GameStagesSyncer.INSTANCE.savePlayerData(event.getPlayerUUID(), compound);
        return compound;
    }
}
