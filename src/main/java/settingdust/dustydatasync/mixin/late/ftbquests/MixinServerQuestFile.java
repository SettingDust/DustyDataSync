package settingdust.dustydatasync.mixin.late.ftbquests;

import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import settingdust.dustydatasync.FTBQuestSyncer;

@Mixin(value = ServerQuestFile.class, remap = false)
public class MixinServerQuestFile {
    @WrapMethod(method = "unload")
    private void dustydatasync$unloadHooks(final Operation<Void> original) {
        FTBQuestSyncer.INSTANCE.setQuestUnloading(true);
        original.call();
        FTBQuestSyncer.INSTANCE.setQuestUnloading(false);
    }
}
