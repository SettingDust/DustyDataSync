package settingdust.dustydatasync.mixin.ftbquests;

import com.feed_the_beast.ftbquests.util.ServerQuestData;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerQuestData.class)
public interface ServerQuestDataAccessor {
    @Invoker(value = "readData", remap = false)
    void dustydatasync$readData(NBTTagCompound nbt);

    @Invoker(value = "writeData", remap = false)
    void dustydatasync$writeData(NBTTagCompound nbt);
}
