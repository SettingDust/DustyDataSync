package settingdust.dustydatasync.mixin.early.serialization;

import net.minecraft.nbt.NBTTagByteArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagByteArray.class)
public interface NBTTagByteArrayAccessor {
    @Accessor
    byte[] getData();

    @Accessor
    void setData(byte[] value);
}
