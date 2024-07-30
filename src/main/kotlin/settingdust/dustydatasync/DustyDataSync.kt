package settingdust.dustydatasync

import com.feed_the_beast.ftbquests.FTBQuests
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.Config.Comment
import net.minecraftforge.common.config.Config.RangeInt
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import sonar.fluxnetworks.FluxNetworks
import zone.rong.mixinbooter.ILateMixinLoader

@Mod(
    modid = Tags.ID,
    name = Tags.NAME,
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    serverSideOnly = true)
object DustyDataSync {
    val logger = LogManager.getLogger()

    val scope = CoroutineScope(Dispatchers.IO)

    lateinit var serverCoroutineScope: CoroutineScope
    lateinit var serverCoroutineDispatcher: CoroutineDispatcher

    @Mod.EventHandler
    fun preInit(event: FMLServerAboutToStartEvent) {
        MinecraftForge.EVENT_BUS.register(this)
        requireNotNull(PlayerLocalLocker)
        serverCoroutineDispatcher =
            MinecraftServerExecutor(event.server).asCoroutineDispatcher()
        serverCoroutineScope = CoroutineScope(SupervisorJob() + serverCoroutineDispatcher)
        if (Loader.isModLoaded("gamestages")) {
            MinecraftForge.EVENT_BUS.register(GameStagesSyncer)
        }
        if (Loader.isModLoaded(FTBQuests.MOD_ID)) {
            MinecraftForge.EVENT_BUS.register(FTBQuestSyncer)
        }
//        if (Loader.isModLoaded(FluxNetworks.MODID)) {
//            MinecraftForge.EVENT_BUS.register(FluxNetworksSyncer)
//        }
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        ConfigManager.sync(Tags.ID, Config.Type.INSTANCE)
        Database.reload()
    }

    @SubscribeEvent
    fun onConfigChangedEvent(event: OnConfigChangedEvent) {
        if (event.modID == Tags.ID) {
            ConfigManager.sync(Tags.ID, Config.Type.INSTANCE)
            Database.reload()
        }
    }

    @Config(modid = Tags.ID, category = "database")
    object Database {
        lateinit var database: org.jetbrains.exposed.sql.Database
            private set

        //        val memory = org.jetbrains.exposed.sql.Database.connect(
        //            "jdbc:h2:~/dustydatasync",
        //            databaseConfig = DatabaseConfig { sqlLogger = Log4jSqlLogger }
        //        )

        @JvmField var databaseUrl = "jdbc:mariadb:://localhost:3306/dusty_data_sync?user=root&password=123456"

        @JvmField @Comment("Retry delay when restoring. Max 5 times. Unit is ms") @RangeInt(min = 0) var syncDelay = 50

        @JvmField @Comment("Output the SQL") var debug = false

        fun reload() {
            database =
                org.jetbrains.exposed.sql.Database.connect(
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = databaseUrl
                            driverClassName = "org.mariadb.jdbc.Driver"
                            maximumPoolSize = 3
                            validate()
                        }),
                    databaseConfig = DatabaseConfig { if (debug) sqlLogger = Log4jSqlLogger })
            TransactionManager.defaultDatabase = database
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    FTBQuestTable, GameStagesTable, FluxNetworksTable)
            }
        }

        private object Log4jSqlLogger : SqlLogger {
            override fun log(context: StatementContext, transaction: Transaction) {
                logger.debug("SQL: ${context.expandArgs(transaction)}")
            }
        }
    }

    @Config(modid = Tags.ID, category = "messages")
    object Messages {
        @JvmField var kickLockMessage = "Current player is locked in database"
    }

    @Suppress("unused")
    class MixinLoader : ILateMixinLoader {
        override fun getMixinConfigs() = listOf("dusty_data_sync.mixins.json")
    }
}

internal val json = Json {
    coerceInputValues = true
    serializersModule = SerializersModule { contextual(NBTTagCompoundSerializer) }
}
internal const val RETRY_COUNT = 5
