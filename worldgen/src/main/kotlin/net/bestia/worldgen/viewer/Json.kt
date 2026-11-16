package net.bestia.worldgen.viewer

/** Just enough JSON for the fact-sheet export, so worldgen needs no JSON library for one tool. */
object Json {

  fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

  private fun append(out: StringBuilder, value: Any?) {
    when (value) {
      null -> out.append("null")
      is String -> string(out, value)
      is Number, is Boolean -> out.append(value.toString())
      is Map<*, *> -> {
        out.append('{')
        value.entries.forEachIndexed { i, (key, item) ->
          if (i > 0) out.append(',')
          string(out, key.toString())
          out.append(':')
          append(out, item)
        }
        out.append('}')
      }
      is Iterable<*> -> {
        out.append('[')
        value.forEachIndexed { i, item ->
          if (i > 0) out.append(',')
          append(out, item)
        }
        out.append(']')
      }
      else -> string(out, value.toString())
    }
  }

  private fun string(out: StringBuilder, text: String) {
    out.append('"')
    for (c in text) {
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\n' -> out.append("\\n")
        c < ' ' -> out.append("\\u%04x".format(c.code))
        else -> out.append(c)
      }
    }
    out.append('"')
  }
}
