package settingdust.dustydatasync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import java.util.logging.Logger

@Mod(
    modid = Tags.ID,
    name = Tags.NAME,
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
object DustyDataSync {
    val scope = CoroutineScope(Dispatchers.IO)

    lateinit var serverCoroutineScope: CoroutineScope
    lateinit var serverCoroutineDispatcher: CoroutineDispatcher

    val hasFTB = Loader.isModLoaded("ftblib")
    val hasFTBQuests = Loader.isModLoaded("ftbquests")
    val hasGameStages = Loader.isModLoaded("gamestages")
    val hasFluxNetworks = Loader.isModLoaded("fluxnetworks")

    @Mod.EventHandler
    fun preInit(event: FMLServerAboutToStartEvent) {
        MinecraftForge.EVENT_BUS.register(this)
        serverCoroutineDispatcher =
            MinecraftServerExecutor(event.server).asCoroutineDispatcher()
        serverCoroutineScope = CoroutineScope(SupervisorJob() + serverCoroutineDispatcher)

        Configurator.setLevel("org.mongodb.driver", Level.INFO)
        Logger.getLogger("org.mongodb.driver").setLevel(java.util.logging.Level.INFO);
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        ConfigManager.sync(Tags.ID, Config.Type.INSTANCE)
        runBlocking { Database.reload() }
    }

    @SubscribeEvent
    fun onConfigChangedEvent(event: OnConfigChangedEvent) {
        if (event.modID == Tags.ID) {
            ConfigManager.sync(Tags.ID, Config.Type.INSTANCE)
            runBlocking { Database.reload() }
        }
    }
}

internal const val RETRY_COUNT = 5
