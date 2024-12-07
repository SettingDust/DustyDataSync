package settingdust.dustydatasync

import com.llamalad7.mixinextras.MixinExtrasBootstrap
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import org.spongepowered.asm.mixin.Mixins
import zone.rong.mixinbooter.ILateMixinLoader

@Suppress("unused")
internal class LateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs() = listOf("${Tags.ID}.mixins.json")
}