package settingdust.dustydatasync

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.Config.Comment
import net.minecraftforge.common.config.Config.RangeInt
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import zone.rong.mixinbooter.ILateMixinLoader

@Mod(
    modid = DustyDataSync.MODID,
    acceptableRemoteVersions = "*",
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter"
)
object DustyDataSync {
    const val MODID = "dusty_data_sync"

    val logger = LogManager.getLogger()

    val scope = CoroutineScope(Dispatchers.Default)

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(this)
        Locks
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        ConfigManager.sync(MODID, Config.Type.INSTANCE)
        Database.reload()
    }

    @SubscribeEvent
    fun onConfigChangedEvent(event: OnConfigChangedEvent) {
        if (event.modID == MODID) {
            ConfigManager.sync(MODID, Config.Type.INSTANCE)
            Database.reload()
        }
    }

    // 最后本地锁定玩家
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        scope.launch {
            player.server.addScheduledTask {
                if (!player.connection.networkManager.isChannelOpen) return@addScheduledTask
                Locks.players += uuid.toString()
                Locks.save()
            }
        }
    }

    // 在所有东西都保存后本地解锁玩家
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player as EntityPlayerMP
        val uuid = player.uniqueID
        scope.launch {
            player.server.addScheduledTask {
                Locks.players -= uuid.toString()
                Locks.save()
            }
        }
    }

    @Config(modid = MODID, category = "database")
    object Database {
        lateinit var database: org.jetbrains.exposed.sql.Database
            private set

        //        val memory = org.jetbrains.exposed.sql.Database.connect(
        //            "jdbc:h2:~/dustydatasync",
        //            databaseConfig = DatabaseConfig { sqlLogger = Log4jSqlLogger }
        //        )

        @JvmField var databaseUrl = "jdbc:mysql://root:123456@localhost:3306/dusty_data_sync"

        @JvmField @Comment("每次重试恢复数据会等待的的延迟时间，重试次数为 5，单位是毫秒") @RangeInt(min = 0) var syncDelay = 50

        fun reload() {
            database =
                org.jetbrains.exposed.sql.Database.connect(
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = databaseUrl
                            driverClassName = "com.mysql.cj.jdbc.Driver"
                            maximumPoolSize = 3
                            validate()
                        }
                    ),
                    databaseConfig = DatabaseConfig { sqlLogger = Log4jSqlLogger }
                )
            TransactionManager.defaultDatabase = database
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    FTBQuestTable,
                    GameStagesTable,
                    FluxNetworksTable
                )
            }
        }

        private object Log4jSqlLogger : SqlLogger {
            override fun log(context: StatementContext, transaction: Transaction) {
                logger.debug("SQL: ${context.expandArgs(transaction)}")
            }
        }
    }

    @Config(modid = MODID, category = "messages")
    object Messages {
        @JvmField var kickLockMessage = "当前玩家在数据库中被锁定"
    }

    class MixinLoader : ILateMixinLoader {
        override fun getMixinConfigs() = listOf("dusty_data_sync.mixins.json")
    }
}

internal val json = Json {
    coerceInputValues = true
    serializersModule = SerializersModule { contextual(NBTTagCompoundSerializer) }
}
internal const val RETRY_COUNT = 5
