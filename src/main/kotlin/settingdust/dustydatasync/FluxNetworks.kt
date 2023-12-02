package settingdust.dustydatasync

import net.minecraft.nbt.NBTTagCompound
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import sonar.fluxnetworks.api.network.IFluxNetwork
import sonar.fluxnetworks.api.utils.NBTType
import sonar.fluxnetworks.common.connection.FluxNetworkServer
import sonar.fluxnetworks.common.data.FluxNetworkData

object FluxNetworksTable : IdTable<Int>() {
    override val id = integer("id").entityId()

    val hash = integer("hash").default(0)

    val data = json<NBTTagCompound>("data", json, NBTTagCompoundSerializer)
}

class FluxNetworksData(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FluxNetworksData>(FluxNetworksTable)

    var hash by FluxNetworksTable.hash
    var data by FluxNetworksTable.data
}

object FluxNetworksSyncer {
    @JvmStatic private val logger = LogManager.getLogger()

    @JvmStatic
    fun onLoadData() {
        val fluxNetworkData = FluxNetworkData.get()

        // 移除不在数据库里的网络
        transaction {
            val networksInDatabase =
                FluxNetworksTable.slice(FluxNetworksTable.id)
                    .select { FluxNetworksTable.id inList fluxNetworkData.networks.keys }
                    .map { it[FluxNetworksTable.id].value }
            fluxNetworkData.networks.keys.removeIf { it !in networksInDatabase }
        }

        transaction {
            for (id in
                FluxNetworksTable.slice(FluxNetworksTable.id).selectAll().map {
                    it[FluxNetworksTable.id].value
                }) {
                fluxNetworkData.networks.putIfAbsent(id, FluxNetworkServer())
                val network = fluxNetworkData.networks[id]!!
                val hash =
                    FluxNetworksTable.slice(FluxNetworksTable.hash)
                        .select { FluxNetworksTable.id eq id }
                        .single()[FluxNetworksTable.hash]
                if (network.toNbt().hashCode() != hash) {
                    val data =
                        FluxNetworksTable.slice(FluxNetworksTable.data)
                            .select { FluxNetworksTable.id eq id }
                            .single()[FluxNetworksTable.data]
                    network.readNetworkNBT(data, NBTType.NETWORK_GENERAL)
                    network.readNetworkNBT(data, NBTType.NETWORK_PLAYERS)
                    logger.debug("加载网络 ${network.networkID} ${network.networkName}")
                }
            }
        }
    }

    @JvmStatic
    fun loadNetwork(id: Int) = transaction {
        val fluxNetworkData = FluxNetworkData.get()
        val data = FluxNetworksData.findById(id) ?: return@transaction
        fluxNetworkData.networks.putIfAbsent(id, FluxNetworkServer())
        val network = fluxNetworkData.networks[id]!!
        if (network.toNbt().hashCode() != data.hash) {
            network.readNetworkNBT(data.data, NBTType.NETWORK_GENERAL)
            network.readNetworkNBT(data.data, NBTType.NETWORK_PLAYERS)
            logger.debug("加载网络 ${network.networkID} ${network.networkName}")
        }
    }

    @JvmStatic
    fun onRemoveNetwork(network: IFluxNetwork) {
        logger.debug("移除网络 ${network.networkID} ${network.networkName}")
        transaction { FluxNetworksData.findById(network.networkID)?.delete() }
    }

    @JvmStatic
    fun onAddNetwork(network: IFluxNetwork) {
        val id = network.networkID
        transaction {
            logger.debug("添加网络 ${network.networkID} ${network.networkName}")
            FluxNetworksTable.insertIgnore {
                it[FluxNetworksTable.id] = id
                val nbt = network.toNbt()
                it[data] = nbt
                it[hash] = nbt.hashCode()
            }
        }
    }

    @JvmStatic
    fun onModify(network: IFluxNetwork) {
        val id = network.networkID
        val nbt = network.toNbt()
        val hashCode = nbt.hashCode()
        transaction {
            if (FluxNetworksData[id].hash != hashCode)
                logger.debug("编辑网络 ${network.networkID} ${network.networkName}")
            FluxNetworksTable.upsert {
                it[FluxNetworksTable.id] = id
                it[data] = nbt
                it[hash] = hashCode
            }
        }
    }

    private fun IFluxNetwork.toNbt() =
        NBTTagCompound().also {
            try {
                FluxNetworkData.writePlayers(this, it)
                writeNetworkNBT(it, NBTType.NETWORK_GENERAL)
            } catch (_: Throwable) {}
        }
}
