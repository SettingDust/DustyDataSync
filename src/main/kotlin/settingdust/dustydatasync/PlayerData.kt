package settingdust.dustydatasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.minecraft.nbt.NBTTagCompound
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.json.json
import java.util.*

internal val json = Json {
    coerceInputValues = true
    serializersModule = SerializersModule {
        contextual(NBTTagCompoundSerializer)
    }
}

object PlayerDatas : IdTable<UUID>() {
    override val id = uuid("id").entityId()

    val lock = bool("lock").default(false)

    val data = json<NBTTagCompound>("data", json, NBTTagCompoundSerializer)

    override val primaryKey = PrimaryKey(id)
}

class PlayerData(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, PlayerData>(PlayerDatas)

    var lock by PlayerDatas.lock

    var data by PlayerDatas.data
}
