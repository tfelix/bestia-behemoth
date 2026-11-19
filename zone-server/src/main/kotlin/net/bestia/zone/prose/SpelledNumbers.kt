package net.bestia.zone.prose

/** Numbers written as words, with their value: "four hundred and eight" is 408, "five seventy-one" is the year 571. */
object SpelledNumbers {

  private val UNITS = (
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen " +
      "seventeen eighteen nineteen"
    ).split(" ").withIndex().associate { it.value to it.index.toLong() }
  private val TENS = "twenty thirty forty fifty sixty seventy eighty ninety".split(" ")
    .withIndex().associate { it.value to 10L * (it.index + 2) }
  private val SCALES = setOf("hundred", "thousand")

  private val NUMBER_WORD = (UNITS.keys + TENS.keys + SCALES).joinToString("|")
  private val SPELLED = Regex("""\b(?:$NUMBER_WORD)(?:[\s-]+(?:and[\s-]+)?(?:$NUMBER_WORD))*\b""", RegexOption.IGNORE_CASE)
  private val SEPARATORS = Regex("""[\s-]+""")

  fun find(text: String): List<Pair<String, Long>> {
    return SPELLED.findAll(text).flatMap { match -> splitOnAnd(match.value).map { it to valueOf(it) } }.toList()
  }

  /** "four hundred and eight" is one number; "five seventy-one and six thirty-six" are two. */
  private fun splitOnAnd(phrase: String): List<String> {
    val parts = ArrayList<String>()
    var current = ArrayList<String>()
    for (word in phrase.split(Regex("""\s+"""))) {
      if (word.lowercase() == "and" && current.isNotEmpty() && current.last().lowercase() !in SCALES) {
        parts += current.joinToString(" ")
        current = ArrayList()
      } else {
        current += word
      }
    }
    if (current.isNotEmpty()) {
      parts += current.joinToString(" ")
    }

    return parts
  }

  private fun valueOf(phrase: String): Long {
    val words = phrase.lowercase().split(SEPARATORS).filter { it != "and" }
    // A year said aloud: "five seventy-one" is 571, not 76.
    if (words.size >= 2 && words[0] in UNITS && words[1] in TENS && words.none { it in SCALES }) {
      return UNITS.getValue(words[0]) * 100 + words.drop(1).sumOf { (UNITS[it] ?: 0) + (TENS[it] ?: 0) }
    }

    var total = 0L
    var current = 0L
    for (word in words) {
      when {
        word in UNITS -> current += UNITS.getValue(word)
        word in TENS -> current += TENS.getValue(word)
        word == "hundred" -> current = maxOf(current, 1) * 100
        word == "thousand" -> {
          total += maxOf(current, 1) * 1000
          current = 0
        }
      }
    }

    return total + current
  }
}
