package settingdust.dustydatasync

import net.minecraft.server.MinecraftServer
import java.util.concurrent.Executor

class MinecraftServerExecutor(private val server: MinecraftServer) : Executor {
    override fun execute(command: Runnable) {
        server.addScheduledTask(command)
    }
}
