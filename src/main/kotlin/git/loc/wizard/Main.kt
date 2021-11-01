package git.loc.wizard

import kotlin.system.exitProcess

private val apps = mapOf(
    "wizard" to Wizard(),
    "library" to LibraryManager(),
)

fun main(vararg args: String) {
    val appSelector = args[0]
    apps[appSelector]?.main(args.drop(1)) ?: run {
        System.err.println("Unknown task '$appSelector'. Available tasks: ${apps.keys.joinToString()}")
        exitProcess(100)
    }
}
