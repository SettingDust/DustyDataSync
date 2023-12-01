package settingdust.dustydatasync.mixin.gamestages;

import com.llamalad7.mixinextras.sugar.Local;
import net.darkhax.gamestages.data.GameStageSaveHandler;
import net.darkhax.gamestages.data.IStageData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import settingdust.dustydatasync.GameStagesSyncer;

@Mixin(value = GameStageSaveHandler.class, remap = false)
public class MixinGameStageSaveHandler {
    @Inject(
            method = "onPlayerLoad",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            shift = At.Shift.BEFORE))
    private static void dustydatasync$loadData(
            PlayerEvent.LoadFromFile event, CallbackInfo ci, @Local IStageData data) {
        GameStagesSyncer.onLoadData(event, data);
    }

    @ModifyVariable(method = "onPlayerSave", at = @At(value = "STORE"))
    private static NBTTagCompound dustydatasync$saveData(NBTTagCompound tag, PlayerEvent.SaveToFile event) {
        GameStagesSyncer.onSaveData(event, tag);
        return tag;
    }
}
