package settingdust.dustydatasync

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.Config.Comment
import net.minecraftforge.common.config.Config.RangeInt
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import zone.rong.mixinbooter.ILateMixinLoader

@Mod(
    modid = DustyDataSync.MODID,
    acceptableRemoteVersions = "*",
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter"
)
object DustyDataSync {
    const val MODID = "dusty_data_sync";
    val logger = LogManager.getLogger()

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

    @Config(modid = MODID, category = "database")
    object Database {
        lateinit var database: org.jetbrains.exposed.sql.Database
            private set

        @JvmField
        var databaseUrl = "jdbc:mysql://root:123456@localhost:3306/dusty_data_sync"
        @JvmField
        @Comment("每次重试恢复数据会等待的的延迟时间，重试次数为 5，单位是毫秒")
        @RangeInt(min = 0)
        var syncDelay = 50

        fun reload() {
            database = org.jetbrains.exposed.sql.Database.connect(
                databaseUrl,
                databaseConfig = DatabaseConfig { sqlLogger = Log4jSqlLogger }
            )
            transaction {
                SchemaUtils.createMissingTablesAndColumns(PlayerDatas)
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
        @JvmField
        var kickLockMessage = "当前玩家在数据库中被锁定"
    }

    class MixinLoader : ILateMixinLoader {
        override fun getMixinConfigs() = listOf("dusty_data_sync.mixins.json")
    }
}
