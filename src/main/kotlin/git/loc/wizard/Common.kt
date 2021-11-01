package git.loc.wizard

import kotlinx.serialization.json.Json

internal val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}
