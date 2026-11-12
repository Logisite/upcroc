package app.upcroc.core.util

import kotlinx.serialization.json.Json

val CoreJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
}

fun cleanUrl(url: String): String {
    if (url.isBlank()) return ""
    return url.substringBefore('?').trimEnd('/')
}
