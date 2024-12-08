package settingdust.dustydatasync

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import net.minecraft.launchwrapper.Launch
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.config.Config
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.KotlinSerializerCodec
import org.bson.codecs.kotlinx.ObjectIdSerializer
import settingdust.dustydatasync.serialization.NBTTagCompoundSerializer
import settingdust.dustydatasync.serialization.TagsModule
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Config(modid = Tags.ID, category = "database")
data object Database {
    @JvmField
    var connectionString: String = "mongodb://root:123456@localhost:27017?appName=${Tags.ID}"

    @JvmField
    var databaseName: String = Tags.ID

    lateinit var client: MongoCluster
        private set

    lateinit var database: MongoDatabase
        private set

    private val bsonConfiguration by lazy {
        BsonConfiguration(
            encodeDefaults = false
        )
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
    private val serializerModule = TagsModule(bsonConfiguration.classDiscriminator) + SerializersModule {
        contextual(Uuid.serializer())
        contextual(ObjectIdSerializer)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
    private val codecRegistry by lazy {
        CodecRegistries.fromCodecs(buildList {
            if (DustyDataSync.hasFTB) {
                add(KotlinSerializerCodec.create<SyncedFTBUniverse>(serializerModule, bsonConfiguration))
                add(KotlinSerializerCodec.create<SyncedFTBPlayer>(serializerModule, bsonConfiguration))
                add(KotlinSerializerCodec.create<SyncedFTBTeam>(serializerModule, bsonConfiguration))
                add(KotlinSerializerCodec.create<SyncedFTBQuest>(serializerModule, bsonConfiguration))
            }
            if (DustyDataSync.hasFTBQuests) {
                add(KotlinSerializerCodec.create<SyncedFTBQuest>(serializerModule, bsonConfiguration))
            }
            if (DustyDataSync.hasFluxNetworks) {
                add(KotlinSerializerCodec.create<SyncedFluxNetwork>(serializerModule, bsonConfiguration))
            }
            if (DustyDataSync.hasGameStages) {
                add(KotlinSerializerCodec.create<SyncedGameStage>(serializerModule, bsonConfiguration))
            }
            add(
                KotlinSerializerCodec.create(
                    NBTTagCompound::class,
                    NBTTagCompoundSerializer,
                    serializerModule,
                    bsonConfiguration
                )
            )
            add(
                KotlinSerializerCodec.create(
                    Uuid::class,
                    Uuid.serializer(),
                    serializerModule,
                    bsonConfiguration
                )
            )
        })
    }


    init {
        Launch.classLoader.addTransformerExclusion("com.mongodb.Jep395RecordCodecProvider")
    }

    suspend fun reload() {
        client = MongoClient.create(MongoClientSettings.builder().apply {
            applyConnectionString(ConnectionString(connectionString))
            codecRegistry(
                CodecRegistries.fromRegistries(
                    codecRegistry,
                    MongoClientSettings.getDefaultCodecRegistry()
                )
            )
        }.build())
        database = client.getDatabase(databaseName)

        runCatching { database.createCollection(SyncedGameStage.COLLECTION) }
        runCatching { database.createCollection(SyncedFluxNetwork.COLLECTION) }

        runCatching { database.createCollection(SyncedFTBTeam.COLLECTION) }
        runCatching {
            database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION).also { collection ->
                collection.createIndex(Indexes.hashed(SyncedFTBTeam::stringId.name))
            }
        }

        runCatching { database.createCollection(SyncedFTBPlayer.COLLECTION) }
        runCatching {
            database.getCollection<SyncedFTBTeam>(SyncedFTBPlayer.COLLECTION).also { collection ->
                collection.createIndex(Indexes.hashed(SyncedFTBPlayer::name.name))
            }
        }

        runCatching { database.createCollection(SyncedFTBUniverse.COLLECTION) }

        runCatching { database.createCollection(SyncedFTBQuest.COLLECTION) }
    }
}