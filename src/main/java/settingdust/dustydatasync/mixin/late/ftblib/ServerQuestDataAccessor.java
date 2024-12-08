package settingdust.dustydatasync.mixin.late.ftblib;

import com.feed_the_beast.ftbquests.util.ServerQuestData;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ServerQuestData.class, remap = false)
public interface ServerQuestDataAccessor {
    @Invoker
    void callReadData(NBTTagCompound nbt);
}
