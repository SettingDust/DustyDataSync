package settingdust.dustydatasync.mixin.late.ftblib;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import settingdust.dustydatasync.FTBLibSyncer;

@Mixin(value = ForgeTeam.class, remap = false)
public class MixinForgeTeam {
    @WrapMethod(method = "markDirty")
    private void dustydatasync$save(final Operation<Void> original) {
        FTBLibSyncer.INSTANCE.saveTeam((ForgeTeam) (Object) this);
    }
}
