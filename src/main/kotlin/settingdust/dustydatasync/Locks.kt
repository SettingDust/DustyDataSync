package settingdust.dustydatasync

import net.minecraftforge.fml.common.Loader
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

object Locks {
    var players: MutableSet<String>
        private set

    private var configFile: Path

    init {
        val configDir = Loader.instance().configDir
        configFile = configDir.toPath() / "locks.txt"
        try {
            configFile.createParentDirectories()
            configFile.createFile()
        } catch (_: Throwable) {
        }
        players = configFile.readLines().toMutableSet()
    }

    fun save() {
        configFile.writeLines(players)
    }
}
