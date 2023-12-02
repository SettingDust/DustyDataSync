package settingdust.dustydatasync.mixin;

import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements Executor {
    @Shadow public abstract ListenableFuture<Object> addScheduledTask(Runnable runnableToSchedule);

    @Override
    public void execute(@NotNull Runnable command) {
        addScheduledTask(command);
    }
}
