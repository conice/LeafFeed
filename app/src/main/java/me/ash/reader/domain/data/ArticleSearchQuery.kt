package me.ash.reader.domain.data

internal fun String.toArticleFtsQuery(): String =
    trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" AND ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
