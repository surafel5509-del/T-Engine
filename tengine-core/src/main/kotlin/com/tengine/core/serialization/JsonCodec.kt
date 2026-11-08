package com.tengine.core.serialization

import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.math.Vector3

/**
 * Zero-dependency RFC 8259 JSON AST, Lexer, Recursive-Descent Parser, and Pretty Printer
 * written from scratch in Kotlin for T-Engine Scene, Prefab, Atlas, and Save serialization.
 */
sealed class JsonValue {
    fun asObjectOrNull(): JsonObject? = this as? JsonObject
    fun asArrayOrNull(): JsonArray? = this as? JsonArray
    fun asStringOrNull(): String? = (this as? JsonString)?.value
    fun asDoubleOrNull(): Double? = (this as? JsonNumber)?.value
    fun asFloatOrNull(): Float? = (this as? JsonNumber)?.value?.toFloat()
    fun asIntOrNull(): Int? = (this as? JsonNumber)?.value?.toInt()
    fun asLongOrNull(): Long? = (this as? JsonNumber)?.value?.toLong()
    fun asBooleanOrNull(): Boolean? = (this as? JsonBool)?.value
}

object JsonNull : JsonValue() {
    override fun toString(): String = "null"
}

data class JsonBool(val value: Boolean) : JsonValue()
data class JsonNumber(val value: Double) : JsonValue() {
    constructor(v: Int) : this(v.toDouble())
    constructor(v: Long) : this(v.toDouble())
    constructor(v: Float) : this(v.toDouble())
}
data class JsonString(val value: String) : JsonValue()

class JsonArray(items: List<JsonValue> = emptyList()) : JsonValue(), Iterable<JsonValue> {
    val elements: MutableList<JsonValue> = items.toMutableList()

    val size: Int get() = elements.size

    operator fun get(index: Int): JsonValue = elements[index]

    fun add(value: JsonValue): JsonArray {
        elements.add(value)
        return this
    }

    fun add(value: String): JsonArray = add(JsonString(value))
    fun add(value: Number): JsonArray = add(JsonNumber(value.toDouble()))
    fun add(value: Boolean): JsonArray = add(JsonBool(value))

    override fun iterator(): Iterator<JsonValue> = elements.iterator()
}

class JsonObject(initial: Map<String, JsonValue> = emptyMap()) : JsonValue() {
    val entriesMap: LinkedHashMap<String, JsonValue> = LinkedHashMap(initial)

    operator fun get(key: String): JsonValue? = entriesMap[key]
    operator fun set(key: String, value: JsonValue) {
        entriesMap[key] = value
    }

    fun put(key: String, value: JsonValue): JsonObject {
        entriesMap[key] = value
        return this
    }

    fun put(key: String, value: String?): JsonObject {
        entriesMap[key] = if (value == null) JsonNull else JsonString(value)
        return this
    }

    fun put(key: String, value: Number): JsonObject {
        entriesMap[key] = JsonNumber(value.toDouble())
        return this
    }

    fun put(key: String, value: Boolean): JsonObject {
        entriesMap[key] = JsonBool(value)
        return this
    }

    fun putVector2(key: String, v: Vector2): JsonObject {
        entriesMap[key] = JsonObject()
            .put("x", v.x)
            .put("y", v.y)
        return this
    }

    fun putVector3(key: String, v: Vector3): JsonObject {
        entriesMap[key] = JsonObject()
            .put("x", v.x)
            .put("y", v.y)
            .put("z", v.z)
        return this
    }

    fun putColor(key: String, c: Color): JsonObject {
        entriesMap[key] = JsonObject()
            .put("r", c.r)
            .put("g", c.g)
            .put("b", c.b)
            .put("a", c.a)
        return this
    }

    fun getString(key: String, default: String = ""): String =
        entriesMap[key]?.asStringOrNull() ?: default

    fun getStringOrNull(key: String): String? =
        entriesMap[key]?.asStringOrNull()

    fun getFloat(key: String, default: Float = 0f): Float =
        entriesMap[key]?.asFloatOrNull() ?: default

    fun getDouble(key: String, default: Double = 0.0): Double =
        entriesMap[key]?.asDoubleOrNull() ?: default

    fun getInt(key: String, default: Int = 0): Int =
        entriesMap[key]?.asIntOrNull() ?: default

    fun getLong(key: String, default: Long = 0L): Long =
        entriesMap[key]?.asLongOrNull() ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        entriesMap[key]?.asBooleanOrNull() ?: default

    fun getObject(key: String): JsonObject? =
        entriesMap[key]?.asObjectOrNull()

    fun getArray(key: String): JsonArray? =
        entriesMap[key]?.asArrayOrNull()

    fun getVector2(key: String, default: Vector2 = Vector2.ZERO): Vector2 {
        val obj = getObject(key) ?: return default
        return Vector2(obj.getFloat("x", default.x), obj.getFloat("y", default.y))
    }

    fun getVector3(key: String, default: Vector3 = Vector3.ZERO): Vector3 {
        val obj = getObject(key) ?: return default
        return Vector3(
            obj.getFloat("x", default.x),
            obj.getFloat("y", default.y),
            obj.getFloat("z", default.z)
        )
    }

    fun getColor(key: String, default: Color = Color.WHITE): Color {
        val obj = getObject(key) ?: return default
        return Color(
            obj.getFloat("r", default.r),
            obj.getFloat("g", default.g),
            obj.getFloat("b", default.b),
            obj.getFloat("a", default.a)
        )
    }
}

class JsonParseException(message: String, val position: Int) : RuntimeException("$message at index $position")

object JsonCodec {

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val result = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isEnd()) {
            throw JsonParseException("Unexpected trailing characters", parser.pos)
        }
        return result
    }

    fun parseObject(text: String): JsonObject =
        parse(text).asObjectOrNull()
            ?: throw JsonParseException("Expected root JSON object", 0)

    fun stringify(value: JsonValue, pretty: Boolean = true): String {
        val sb = StringBuilder(256)
        writeValue(sb, value, pretty, 0)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: JsonValue, pretty: Boolean, depth: Int) {
        when (value) {
            is JsonNull -> sb.append("null")
            is JsonBool -> sb.append(if (value.value) "true" else "false")
            is JsonNumber -> {
                val d = value.value
                if (!d.isFinite()) {
                    sb.append("0")
                } else if (d == d.toLong().toDouble()) {
                    sb.append(d.toLong())
                } else {
                    // Trim noisy float conversion artifacts
                    val formatted = d.toFloat().toString()
                    sb.append(formatted)
                }
            }
            is JsonString -> writeEscapedString(sb, value.value)
            is JsonArray -> {
                if (value.elements.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                for (i in value.elements.indices) {
                    if (pretty) {
                        sb.append('\n')
                        indent(sb, depth + 1)
                    }
                    writeValue(sb, value.elements[i], pretty, depth + 1)
                    if (i < value.elements.size - 1) sb.append(',')
                }
                if (pretty) {
                    sb.append('\n')
                    indent(sb, depth)
                }
                sb.append(']')
            }
            is JsonObject -> {
                if (value.entriesMap.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                val entries = value.entriesMap.entries.toList()
                for (i in entries.indices) {
                    val (k, v) = entries[i]
                    if (pretty) {
                        sb.append('\n')
                        indent(sb, depth + 1)
                    }
                    writeEscapedString(sb, k)
                    sb.append(if (pretty) ": " else ":")
                    writeValue(sb, v, pretty, depth + 1)
                    if (i < entries.size - 1) sb.append(',')
                }
                if (pretty) {
                    sb.append('\n')
                    indent(sb, depth)
                }
                sb.append('}')
            }
        }
    }

    private fun indent(sb: StringBuilder, depth: Int) {
        repeat(depth * 2) { sb.append(' ') }
    }

    private fun writeEscapedString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val src: String) {
        var pos: Int = 0

        fun isEnd(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length) {
                when (src[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> break
                }
            }
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (isEnd()) throw JsonParseException("Unexpected end of input", pos)
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonString(parseString())
                't' -> {
                    expectLiteral("true")
                    JsonBool(true)
                }
                'f' -> {
                    expectLiteral("false")
                    JsonBool(false)
                }
                'n' -> {
                    expectLiteral("null")
                    JsonNull
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw JsonParseException("Unexpected character '$ch'", pos)
            }
        }

        private fun expectLiteral(literal: String) {
            if (!src.startsWith(literal, pos)) {
                throw JsonParseException("Expected '$literal'", pos)
            }
            pos += literal.length
        }

        private fun parseObject(): JsonObject {
            pos++ // '{'
            skipWhitespace()
            val obj = JsonObject()
            if (!isEnd() && src[pos] == '}') {
                pos++
                return obj
            }
            while (true) {
                skipWhitespace()
                if (isEnd() || src[pos] != '"') {
                    throw JsonParseException("Expected string key in JSON object", pos)
                }
                val key = parseString()
                skipWhitespace()
                if (isEnd() || src[pos] != ':') {
                    throw JsonParseException("Expected ':' after object key '$key'", pos)
                }
                pos++
                val value = parseValue()
                obj[key] = value
                skipWhitespace()
                if (isEnd()) throw JsonParseException("Unterminated JSON object", pos)
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return obj
                    }
                    else -> throw JsonParseException("Expected ',' or '}' in JSON object", pos)
                }
            }
        }

        private fun parseArray(): JsonArray {
            pos++ // '['
            skipWhitespace()
            val arr = JsonArray()
            if (!isEnd() && src[pos] == ']') {
                pos++
                return arr
            }
            while (true) {
                arr.add(parseValue())
                skipWhitespace()
                if (isEnd()) throw JsonParseException("Unterminated JSON array", pos)
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return arr
                    }
                    else -> throw JsonParseException("Expected ',' or ']' in JSON array", pos)
                }
            }
        }

        private fun parseString(): String {
            pos++ // opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw JsonParseException("Unterminated escape sequence", pos)
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) {
                                    throw JsonParseException("Incomplete unicode escape", pos)
                                }
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonParseException("Invalid unicode escape '\\u$hex'", pos)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("Invalid escape character '\\$esc'", pos)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw JsonParseException("Unterminated JSON string", pos)
        }

        private fun parseNumber(): JsonNumber {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
            if (pos < src.length && src[pos] == '.') {
                pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            val parsed = token.toDoubleOrNull()
                ?: throw JsonParseException("Invalid number '$token'", start)
            return JsonNumber(parsed)
        }
    }
}
