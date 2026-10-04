package com.engperini.esp32flashingapp.flash

data class FlashImage(val address: Long, val path: String)
data class FlashPlan(val chip: String = "esp32s3", val baudRate: Int = 460800, val images: List<FlashImage>)

object FlashArgsParser {
    private val address = Regex("""^(?:0[xX])?([0-9a-fA-F]+)$""")

    fun parse(content: String): List<FlashImage> = parseTokens(tokenize(content))
    fun parse(tokens: List<String>): List<FlashImage> = parseTokens(tokens)

    private fun parseTokens(tokens: List<String>): List<FlashImage> {
        val images = mutableListOf<FlashImage>()
        var index = 0
        while (index < tokens.size) {
            val match = address.matchEntire(tokens[index])
            if (match != null && index + 1 < tokens.size) {
                val path = tokens[index + 1]
                if (!path.startsWith("-") && !address.matches(path)) {
                    images += FlashImage(match.groupValues[1].toLong(16), path)
                    index += 2
                    continue
                }
            }
            index++
        }
        return images
    }

    internal fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        fun flush() { if (current.isNotEmpty()) { result += current.toString(); current.clear() } }
        for (char in text) {
            if (escaped) { current.append(char); escaped = false }
            else if (char == '\\' && quote != '\'') escaped = true
            else if (quote != null) { if (char == quote) quote = null else current.append(char) }
            else when {
                char == '"' || char == '\'' -> quote = char
                char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }
        require(quote == null) { "Unterminated quote in flash_args" }
        if (escaped) current.append('\\')
        flush()
        return result
    }
}
