package git.loc.wizard

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.appendText
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.system.exitProcess

private val urlExtractor = """^https?://[^\s]*""".toRegex()

class Wizard : CliktCommand() {
    val inputFile by option(
        "-i", "--input-file",
        help = "File containing a list of git repos to analyze (1 per line)"
    ).path(
        mustExist = true,
        canBeFile = true,
        canBeDir = false,
        mustBeReadable = true
    ).required()

    val outputFile by option(
        "-o", "--output-file",
        help = "File to write cloc output to"
    ).path(canBeDir = false)

    val library by option(
        "-l", "--library",
        help = "Output any new data to this file, update existing data there"
    ).convert {
        RepoLibrary(Path.of(it))
    }

    val refreshLibrary by option(
        "--refresh-library",
        help = "Re-downloads and re-calculates everything, even if it was already in the library file. Has no effect without '--library-file'."
    ).flag()

    val gitCommand by option(
        "-G", "--git-cmd",
        help = "The git command to use"
    ).default("git")

    val gitArgs by option(
        "-g", "--git-args",
        help = "Additional arguments to be used with the git command"
    ).convert {
        it.split(" ")
    }.default(listOf("clone", "--depth", "1"))

    val clocCommand by option(
        "-C", "--cloc-cmd",
        help = "The cloc command to use (to count the lines of code)"
    ).default("cloc")

    val clocArgs by option(
        "-c", "--cloc-args",
        help = "Additional arguments to be used with the cloc command"
    ).convert {
        it.split(" ")
    }.default(emptyList())

    val raw by option(
        "-r", "--raw",
        help = "Write raw cloc output, do not process (contains more data but no analysis is performed)"
    ).flag()

    val seart by option(
        "--seart",
        help = "Enables seart-style json parsing for input file (see https://seart-ghs.si.usi.ch)"
    ).flag()

    @OptIn(ExperimentalSerializationApi::class)
    override fun run() {
        if (raw && library != null) {
            System.err.println("Cannot use --raw with --library-file")
            exitProcess(2)
        }

        val reposToDo =
            if (library != null && !refreshLibrary) {
                parseFile().filter { gitRepo ->
                    if (library!!.content.containsKey(gitRepo.url)) {
                        println("Skipping ${gitRepo.url} (already in library).")
                        false
                    } else true
                }
            } else parseFile()

        val startProgressCounter = AtomicInteger(1)
        val doneProgressCounter = AtomicInteger(1)
        val total = reposToDo.size

        val results = runBlocking {
            reposToDo.map { repo ->
                async(Dispatchers.IO) {
                    repo.url?.let { url ->
                        val tmpDir = Files.createTempDirectory(null)
                        val startPosition = startProgressCounter.getAndIncrement()
                        println("Analyzing $startPosition/$total: $url (in $tmpDir)...")
                        runCommand(gitCommand, gitArgs, url, tmpDir.toString()).waitFor()
                        val finalClocArgs = if (raw) clocArgs else clocArgs + "--json"
                        val clocStdOut =
                            runCommand(clocCommand, finalClocArgs, tmpDir.toString()).inputStream.readAllBytes().decodeToString()
                        tmpDir.toFile().deleteRecursively()
                        println("Done ${doneProgressCounter.getAndIncrement()}/$total [#$startPosition]: $url")
                        clocStdOut to url
                    } ?: run {
                        System.err.println("Error: ${repo.error}")
                        null
                    }
                }
            }.mapNotNull {
                it.await()
            }
        }

        val jsonResults by lazy {
            println("Computing cloc JSON data...")
            results.mapNotNull { (clocStdOut, url) ->
                runCatching {
                    val parsedOutput = json.decodeFromString<ClocOutputData>(clocStdOut)
                    ParsedResultData(url, parsedOutput)
                }.getOrElse {
                    //System.err.println("Could not find Kotlin LOCs for $url. Setting to -1.")
                    //ParsedResultData(url, ClocOutputData(ClocHeader(-1, -1), ClocKotlin(-1, -1, -1)))
                    System.err.println("Could not find Kotlin LOCs for $url. Ignoring.")
                    null
                }
            }.sortedByDescending {
                it.clocOutput.Kotlin.code
            }
        }

        library?.let { libNN ->
            println("Updating library...")
            jsonResults.forEach { result ->
                libNN.content[result.url] = result
            }
            libNN.writeToFile()
        }

        outputFile?.deleteIfExists()
        if (raw) {
            results.map { (clocStdOut, url) ->
                "######## $url ########\n$clocStdOut"
            }
        } else {
            jsonResults.map { json.encodeToString(it) }
        }.forEach { text ->
            outputFile?.appendText(text) ?: println(text)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseFile() = if (!seart) {
        inputFile.readLines().map { line ->
            val extractedUrl = urlExtractor.find(line)
            extractedUrl?.value?.let { request ->
                GitRepo(request)
            } ?: GitRepo(null, "Cannot parse '$line' to valid URL.")
        }
    } else {
        json.decodeFromString<SeartData>(inputFile.readText()).items.map { GitRepo(it.url()) }
    }

    private fun runCommand(command: String, args: List<String>, vararg moreArgs: String) =
        Runtime.getRuntime().exec(arrayOf(command) + args.toTypedArray() + moreArgs)
}

data class GitRepo(val url: String?, val error: String? = null)
