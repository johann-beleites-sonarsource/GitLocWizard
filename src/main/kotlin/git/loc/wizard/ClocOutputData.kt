package git.loc.wizard

import kotlinx.serialization.Serializable

@Serializable
data class ParsedResultData(val url: String, val clocOutput: ClocOutputData)

@Serializable
data class ClocOutputData(val header: ClocHeader, val Kotlin: ClocKotlin)

@Serializable
data class ClocHeader(val n_files: Int, val n_lines: Int)

@Serializable
data class ClocKotlin(val nFiles: Int, val comment: Int, val code: Int)
