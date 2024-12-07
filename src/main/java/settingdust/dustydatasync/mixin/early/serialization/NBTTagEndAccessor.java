package settingdust.dustydatasync.mixin.early.serialization;

import net.minecraft.nbt.NBTTagEnd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NBTTagEnd.class)
public interface NBTTagEndAccessor {
    @Invoker("<init>")
    static NBTTagEnd construct() {
        throw new IllegalStateException();
    }
}
