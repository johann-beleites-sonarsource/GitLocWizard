package git.loc.wizard

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import java.nio.file.Path


class LibraryManager : CliktCommand() {
    val showLargest by option(
        "--show-largest",
        help = "Show largest n projects"
    ).int()

    val count by option(
        "--count",
        help = "Count number of entries in the library (including those with the same name)"
    ).flag()

    val countUnique by option(
        "--count-unique",
        help = "Count number of repos in the library with different name"
    ).flag()

    val library by option(
        "-l", "--library",
        help = "Library file to use"
    ).convert {
        RepoLibrary(Path.of(it))
    }.required()

    override fun run() {
        if (count) {
            println("Total entries: ${library.content.values.size}")
        }

        if (countUnique) {
            println("Unique entries: ${library.content.values.groupBy { extractNameFromUrl(it.url) }.size}")
        }

        showLargest?.let { numberOfProjects ->
            library.content.values
                .sortedByDescending { it.clocOutput.Kotlin.code }
                .subList(0, numberOfProjects)
                .joinToString("\n") { "${it.clocOutput.Kotlin.code}: ${it.url}" }
                .let { println(it) }
        }
    }
}

private const val NAME_GROUP = "name"
private fun extractNameFromUrl(url: String) =
    """(https?://)?[^/:]+/[^/]+/(?<${NAME_GROUP}>.*)""".toRegex()
        .matchEntire(url)
        ?.groups?.get(NAME_GROUP)
        ?.value

