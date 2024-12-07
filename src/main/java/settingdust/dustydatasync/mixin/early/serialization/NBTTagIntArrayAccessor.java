package settingdust.dustydatasync.mixin.early.serialization;

import net.minecraft.nbt.NBTTagIntArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagIntArray.class)
public interface NBTTagIntArrayAccessor {
    @Accessor("intArray")
    int[] getData();

    @Accessor("intArray")
    void setData(int[] value);
}
