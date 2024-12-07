package settingdust.dustydatasync.mixin.early.serialization;

import net.minecraft.nbt.NBTBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NBTBase.class)
public interface NBTBaseAccessor {
    @Invoker("getString")
    String getString();
}
