package git.loc.wizard

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import java.nio.file.Path


class LibraryManager : CliktCommand() {
    val showLargest by option(
        "--show-largest",
        help = "Show largest n projects"
    ).int()

    val library by option(
        "-l", "--library",
        help = "Library file to use"
    ).convert {
        RepoLibrary(Path.of(it))
    }.required()

    override fun run() {
        showLargest?.let { numberOfProjects ->
            library.content.values
                .sortedByDescending { it.clocOutput.Kotlin.code }
                .subList(0, numberOfProjects)
                .joinToString("\n") { "${it.clocOutput.Kotlin.code}: ${it.url}" }
                .let { println(it) }
        }
    }
}
