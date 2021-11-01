package git.loc.wizard

import kotlinx.serialization.Serializable

@Serializable
data class SeartData(val items: List<SeartItem>)

@Serializable
data class SeartItem(val name: String) {
    fun url() = "https://github.com/${this.name}"
}
