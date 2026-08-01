package net.bestia.worldgen.core

/**
 * A params file was not usable. Its message names the origin and the line, and is meant to be printed as it is.
 *
 * A type of its own rather than a bare `IllegalArgumentException` because the two failures are different for a
 * caller: a bad *file* is a thing the person running the tool can fix and wants a legible message about, while a
 * bad *value* reaching a params constructor is a programming error. The viewer catches this one and prints it
 * without a stack trace; it does not catch the other.
 */
class ParamsTextException(message: String) : IllegalArgumentException(message)

/**
 * The params file: a flat list of dotted keys, parsed but not yet applied.
 *
 * ```
 * params-format = 1                 # required, and required first
 * tectonics.oceanicShare  = 0.45
 * tectonics.plateSpacing  = default # the word. An empty value is an error, not a derivation
 * erosion.basins.spacing  = 75000   # 75_000.0 is Kotlin syntax and is refused by name
 * town.streets.rings      = 0.28, 0.55, 0.82
 * ```
 *
 * ### Why this is hand-written
 *
 * `java.util.Properties` is free, in the JDK, and wrong for this. It is a `Hashtable`, so it has no line numbers
 * and "unknown key at line 12" cannot be expressed; a duplicate key silently wins last, which is the precise
 * silent-merge failure a params file exists to remove; and `load` applies backslash escaping and accepts `:` as a
 * separator, so two files that look identical are not. Owning about two hundred lines buys line numbers,
 * duplicate detection and nearest-key suggestions. `store/RleCodec.kt` set the precedent for owning a codec
 * rather than importing one.
 *
 * It would also breach the module's dependency rule, which `checkBoundaries` enforces: `worldgen` compiles
 * against kotlin-stdlib and nothing else, and reads no files. So [parse] takes the text and an [origin] label
 * that is only ever used in messages - **never opened** - exactly as `ChunkBlobStore` keeps I/O with its caller.
 *
 * ### The schema is derived, not declared
 *
 * A reader records every key it asks for, whether the file sets it or not. Since a loader asks for all of its
 * fields, the set of asked-for keys *is* the schema, and anything left in the file that nobody asked for is
 * unknown - reported with its line number and the nearest key that does exist. Nothing has to be kept in step:
 * unlike `WorldArgs.WORLD_FLAGS`, which is a declared list because it is checked against `shapeVersion`, a
 * derived set cannot go stale. It is also what lets a *deliberately* absent key be caught: the four values
 * `WorldParams.resolved` forwards are asked for by nobody, so setting one is an error rather than a value that
 * is silently overwritten a moment later.
 *
 * ### Rules, and the reason for each
 *
 * - **`params-format` first.** A file whose first key is something else is probably not a params file at all.
 * - **`default` for a derived tunable, never an empty value.** An empty value is what a truncated file or a bad
 *   shell expansion produces, and must not quietly mean "derive it from the world size".
 * - **A duplicate key is an error naming both lines.** Last-wins is right for a command line, where
 *   `-Pgenesis -Pseed=7` layering is the point, and wrong for a file, where it is a merge nobody saw.
 * - **No underscores in numbers.** `75_000.0` is Kotlin, and a reader who copies a default out of the source
 *   should be told so rather than have the value silently refused as unparseable.
 * - **Non-finite numbers are refused.** This is the one complete guard against a NaN reaching a params class:
 *   NaN has many bit patterns and `toRawBits` would fingerprint them differently, so a digest could differ
 *   between two runs that read the same file. The per-class `require` blocks catch it too, but only in the
 *   fields whose bounds happen to exclude it; this catches every field.
 */
class ParamsText private constructor(private val origin: String, private val entries: Map<String, Entry>) {

  private class Entry(val value: String, val line: Int)

  /**
   * Every key any reader asked for: the derived schema.
   *
   * Insertion-ordered so the suggestions and the `digestNames() == loaderKeys()` test report in the order the
   * loaders are written, which is the order the fields are declared.
   */
  private val requested = LinkedHashSet<String>()

  /** For the test that asserts a loader asks for exactly the fields its digest folds. */
  val requestedKeys: Set<String> get() = requested

  /**
   * The keys this file actually changed something with, in file order: the overrides, for the startup line.
   *
   * Only meaningful once every loader has run, for the same reason [checkAllConsumed] is. Printing these is the
   * point of the whole phase - it is what turns "these two runs disagree" from a mystery into a diff - so they
   * are listed rather than counted.
   */
  val consumedKeys: List<String> get() = entries.keys.filter { it in requested }

  /** A view of this file rooted at one key prefix. See [ParamsSource]. */
  fun scope(prefix: String) = ParamsSource(prefix)

  /**
   * The readers, rooted at a prefix, so a params class names its own fields and not its position in the tree.
   *
   * A view rather than a copy: every read goes back to the one [ParamsText], so consumption is tracked globally
   * and a nested class's keys are recorded under their full dotted names.
   */
  inner class ParamsSource internal constructor(private val prefix: String) {

    /** A nested params object's keys - `erosion.basins.spacing` is `scope("erosion").scope("basins")`. */
    fun scope(nested: String) = ParamsSource("$prefix.$nested")

    /**
     * The file's value for [key], or [current] if it says nothing.
     *
     * Taking the current value as the fallback rather than consulting a table of defaults is what keeps the
     * defaults in one place - the constructor - by construction. It is the idiom `WorldArgs.worldConfig(base)`
     * already uses, and it means an absent key evaluates to the receiver's own field.
     */
    fun double(key: String, current: Double): Double {
      val entry = entryFor(key) ?: return current
      return asDouble(qualify(key), entry)
    }

    /**
     * A tunable whose absence means "derive it from the world", spelled `default` in the file.
     *
     * The word rather than an empty value, and rather than a magic number: `plateSpacing = default` says the
     * thing the reader means, and there is no double that cannot also be a real spacing.
     */
    fun doubleOrDerived(key: String, current: Double?): Double? {
      val entry = entryFor(key) ?: return current
      if (entry.value == DERIVED) return null
      return asDouble(qualify(key), entry)
    }

    fun int(key: String, current: Int): Int {
      val entry = entryFor(key) ?: return current
      val text = plainNumber(qualify(key), entry)
      return text.toIntOrNull()
        ?: fail(entry.line, "${qualify(key)} expects a whole number, found '${entry.value}'")
    }

    fun boolean(key: String, current: Boolean): Boolean {
      val entry = entryFor(key) ?: return current
      return when (entry.value) {
        "true" -> true
        "false" -> false
        else -> fail(entry.line, "${qualify(key)} expects true or false, found '${entry.value}'")
      }
    }

    /**
     * A comma-separated list of doubles, with at least one element.
     *
     * An empty list is refused for the same reason an empty value is: it is what a half-written line looks
     * like, and a caller that genuinely wants no rings can say so in code.
     */
    fun doubleList(key: String, current: List<Double>): List<Double> {
      val entry = entryFor(key) ?: return current
      val parts = entry.value.split(',').map { it.trim() }
      if (parts.any { it.isEmpty() }) {
        fail(entry.line, "${qualify(key)} has an empty element in '${entry.value}'")
      }
      return parts.map { asDouble(qualify(key), Entry(it, entry.line)) }
    }

    /** An enum, by name, case-insensitively - a file is written by hand and `AGRARIAN` is shouting. */
    fun <T : Enum<T>> enum(key: String, current: T, values: Array<T>): T {
      val entry = entryFor(key) ?: return current
      return values.firstOrNull { it.name.equals(entry.value, ignoreCase = true) }
        ?: fail(
          entry.line,
          "${qualify(key)} expects one of ${values.joinToString(", ") { it.name }}, found '${entry.value}'"
        )
    }

    private fun qualify(key: String) = "$prefix.$key"

    private fun entryFor(key: String): Entry? {
      val full = qualify(key)
      requested.add(full)
      return entries[full]
    }
  }

  private fun asDouble(key: String, entry: Entry): Double {
    val text = plainNumber(key, entry)
    val value = text.toDoubleOrNull() ?: fail(entry.line, "$key expects a number, found '${entry.value}'")
    // The one place NaN and the infinities are stopped for every field at once. See the class KDoc.
    if (!value.isFinite()) fail(entry.line, "$key must be a finite number, found '${entry.value}'")
    return value
  }

  private fun plainNumber(key: String, entry: Entry): String {
    if (entry.value.isEmpty()) {
      fail(entry.line, "$key has no value. To derive a value from the world, write '$DERIVED'")
    }
    if (entry.value == DERIVED) {
      fail(entry.line, "$key is not a derived tunable, so '$DERIVED' is not a value it can take")
    }
    if (entry.value.contains('_')) {
      fail(entry.line, "$key: '${entry.value}' has underscores in it, which are Kotlin syntax and not this format")
    }
    return entry.value
  }

  /**
   * Reports every key nobody asked for, with its line and the nearest key that exists.
   *
   * Called once, after all the loaders have run, so that "asked for" is complete - which is why
   * `WorldParams.load` owns the call rather than leaving it to whoever built the file.
   *
   * [notYetLoadable] is the queue of prefixes whose loaders are not written yet. A key under one of those is a
   * different message from a misspelling: the reader spelled a real tunable correctly and it simply cannot be
   * set from a file today, which is worth saying rather than suggesting they meant something else.
   */
  fun checkAllConsumed(notYetLoadable: Set<String> = emptySet()) {
    val unknown = entries.keys.filter { it != FORMAT_KEY && it !in requested }
    if (unknown.isEmpty()) return

    val (queued, misspelled) = unknown.partition { key -> notYetLoadable.any { key == it || key.startsWith("$it.") } }

    val lines = ArrayList<String>()
    for (key in queued.sortedBy { entries.getValue(it).line }) {
      lines += "  line ${entries.getValue(key).line}: $key cannot be set from a file yet"
    }
    for (key in misspelled.sortedBy { entries.getValue(it).line }) {
      val nearest = nearestTo(key)
      val hint = if (nearest == null) "" else ", did you mean $nearest?"
      lines += "  line ${entries.getValue(key).line}: $key is not a tunable$hint"
    }

    throw ParamsTextException("$origin sets ${unknown.size} key(s) nothing reads:\n" + lines.joinToString("\n"))
  }

  /**
   * The closest key the loaders do read, or null if nothing is close.
   *
   * Edit distance over the whole dotted key, so a key in the wrong section is found as readily as a
   * misspelled field. The threshold scales with length because a long key can afford a typo or two and a short
   * one cannot: at a fixed distance of 2, `town.setback` would "suggest" half the file.
   */
  private fun nearestTo(key: String): String? {
    val limit = maxOf(2, key.length / 5)
    return requested
      .map { it to editDistance(key, it) }
      .filter { it.second <= limit }
      .minByOrNull { it.second }
      ?.first
  }

  private fun fail(line: Int, message: String): Nothing = throw ParamsTextException("$origin line $line: $message")

  companion object {

    /** The word that means "derive this from the world" for a nullable tunable. */
    const val DERIVED = "default"

    /** The required first key, and the only one that is not a tunable. */
    const val FORMAT_KEY = "params-format"

    /** The format this parser understands. Bumped only if the *syntax* changes, never for a new tunable. */
    const val FORMAT_VERSION = 1

    private val KEY = Regex("""[A-Za-z][A-Za-z0-9-]*(\.[A-Za-z][A-Za-z0-9]*)*""")

    /**
     * Parses [text], which came from wherever the caller likes; [origin] labels it in messages and is never
     * opened.
     */
    fun parse(text: String, origin: String): ParamsText {
      val entries = LinkedHashMap<String, Entry>()
      var sawFormat = false

      text.lineSequence().forEachIndexed { index, raw ->
        val line = index + 1
        val body = raw.substringBefore('#').trim()
        if (body.isEmpty()) return@forEachIndexed

        val split = body.indexOf('=')
        if (split < 0) {
          throw ParamsTextException("$origin line $line: expected 'key = value', found '$body'")
        }

        val key = body.substring(0, split).trim()
        val value = body.substring(split + 1).trim()

        if (!KEY.matches(key)) {
          throw ParamsTextException("$origin line $line: '$key' is not a well-formed key")
        }

        if (!sawFormat) {
          if (key != FORMAT_KEY) {
            throw ParamsTextException(
              "$origin line $line: the first key must be '$FORMAT_KEY = $FORMAT_VERSION', found '$key'"
            )
          }
          if (value.toIntOrNull() != FORMAT_VERSION) {
            throw ParamsTextException(
              "$origin line $line: $FORMAT_KEY is '$value', but this build reads format $FORMAT_VERSION"
            )
          }
          sawFormat = true
        }

        val existing = entries[key]
        if (existing != null) {
          throw ParamsTextException(
            "$origin: $key is set twice, on lines ${existing.line} and $line. " +
                "A file is merged by nobody, so the later one does not silently win"
          )
        }

        entries[key] = Entry(value, line)
      }

      if (!sawFormat) {
        throw ParamsTextException("$origin has no '$FORMAT_KEY = $FORMAT_VERSION' line, so it is not a params file")
      }

      return ParamsText(origin, entries)
    }

    /** Levenshtein distance, two rows at a time. Keys are short and this runs once per unknown key. */
    private fun editDistance(a: String, b: String): Int {
      var previous = IntArray(b.length + 1) { it }
      var current = IntArray(b.length + 1)

      for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
          val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
          current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
        }
        val swap = previous
        previous = current
        current = swap
      }

      return previous[b.length]
    }
  }
}
