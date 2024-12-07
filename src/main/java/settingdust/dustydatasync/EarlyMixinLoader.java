package settingdust.dustydatasync;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

public class EarlyMixinLoader implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {
        Mixins.addConfigurations(Tags.ID + ".early.mixins.json");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
