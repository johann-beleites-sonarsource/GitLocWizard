package git.loc.wizard

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

@OptIn(ExperimentalSerializationApi::class)
class RepoLibrary(private val path: Path) {

    init {
        runCatching {
            if (!path.exists()) {
                path.createFile()
                path.writeText("[]")
            }
        }.onFailure {
            System.err.println("Could not stat or create library file '$path'")
            exitProcess(2001)
        }
    }

    val content by lazy {
        json.decodeFromString<List<ParsedResultData>>(path.readText()).associateBy { it.url }.toMutableMap()
    }

    fun writeToFile() {
        path.writeText(json.encodeToString(content.values.toSet()))
    }
}
