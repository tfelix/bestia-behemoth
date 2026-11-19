package net.bestia.zone.prose

import net.bestia.zone.text.TextBrief
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Checks a written text against its brief. Every proper name and every number in the text must come from the
 * brief, and the text must name its lead. This is what keeps a model from adding to the history of the world.
 *
 * It cannot see a true name in the wrong place: a sacking moved to another town passes.
 */
@Component
class ProseValidator {

  class Problem(val type: Type, val value: String)

  enum class Type { NAME, TITLE, CASE, LOWERCASE, NUMBER, MISSING }

  private val englishWords: Set<String> by lazy {
    ClassPathResource(WORDS).inputStream.bufferedReader().useLines { it.toHashSet() }
  }

  /** Empty when the text may be shown. */
  fun check(text: String, brief: TextBrief): List<Problem> {
    val allowed = allowedTextOf(brief)
    val allowedWords = WORD_LIKE.findAll(allowed).map { it.value }.toSet()

    return wrongNames(text, allowed, allowedWords) +
      wrongCase(text, allowed) +
      wrongNumbers(text, allowed) +
      lowercaseStart(text) +
      brief.mustName.filterNot { it in text }.map { Problem(Type.MISSING, it) }
  }

  /** One sentence per problem, for the next try. */
  fun feedback(problems: List<Problem>): String {
    return problems.joinToString(" ") { problem ->
      when (problem.type) {
        Type.NAME -> "\"${problem.value}\" is not a name in the fact sheet. Use only names from the sheet."
        Type.TITLE -> "Do not add the title in \"${problem.value}\". Write the name exactly as the sheet does."
        Type.CASE -> "Write \"${problem.value}\" with capital letters, exactly as the fact sheet does."
        Type.LOWERCASE -> "A sentence starts with \"${problem.value}\". Start every sentence with a capital letter."
        Type.NUMBER -> "The number ${problem.value} is not in the fact sheet. Use only years and numbers from the sheet."
        Type.MISSING -> "The text must name ${problem.value}."
      }
    }
  }

  private fun allowedTextOf(brief: TextBrief): String {
    return (brief.allowedNames.flatMap { listOf(it.name, it.what) } + brief.facts + brief.task).joinToString("\n")
  }

  private fun wrongNames(text: String, allowed: String, allowedWords: Set<String>): List<Problem> {
    val starts = sentenceStarts(text)

    return PHRASE.findAll(text).mapNotNull { match ->
      val phrase = withoutPossessive(match.value.trim())
      var words = phrase.split(SPACES)
      var atStart = match.range.first in starts

      // A capital at the start of a sentence is grammar, and "O" or "Thy" is the voice of a hymn.
      while (words.isNotEmpty() && (words[0] in STYLE_WORDS || (atStart && isCommonWord(words[0], allowedWords)))) {
        words = words.drop(1)
        atStart = false
      }
      words = words.dropWhile { it in CONNECTORS }
      if (words.isEmpty()) {
        return@mapNotNull null
      }

      val title = words[0].takeIf { it in TITLES && words.size > 1 }
      if (title != null) {
        words = words.drop(1)
      }
      val name = words.joinToString(" ")
      when {
        name in allowed -> title?.let { Problem(Type.TITLE, "$it $name") }
        words.size == 1 && name in allowedWords -> null
        // "Temujin of Karaobo": two allowed names joined. Whether he came from there is not checked.
        name.split(OF).let { parts -> parts.size > 1 && parts.all { it in allowed } } -> null
        else -> Problem(Type.NAME, phrase)
      }
    }.toList()
  }

  private fun isCommonWord(word: String, allowedWords: Set<String>): Boolean {
    return word.lowercase() in englishWords && word !in allowedWords
  }

  /** An allowed name in other letter case, "keshband" for "Keshband". Small models do this. */
  private fun wrongCase(text: String, allowed: String): List<Problem> {
    val names = CAPITALISED.findAll(allowed).map { it.value }.filter { it.lowercase() !in englishWords }.toSortedSet()

    return names.mapNotNull { name ->
      Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE).findAll(text)
        .firstOrNull { it.value != name }
        ?.let { Problem(Type.CASE, it.value) }
    }
  }

  private fun wrongNumbers(text: String, allowed: String): List<Problem> {
    val allowedNumbers = NUMBER.findAll(allowed).map { valueOf(it.value) }.toSet()
    val digits = NUMBER.findAll(text).filter { valueOf(it.value) !in allowedNumbers }.map { Problem(Type.NUMBER, it.value) }
    // "The two peoples" is style. A count of dead or a span of years written out is a fact.
    val spelled = SpelledNumbers.find(text)
      .filter { (_, value) -> value > SMALL_COUNT && value !in allowedNumbers }
      .map { (words, _) -> Problem(Type.NUMBER, words) }

    return digits.toList() + spelled
  }

  private fun lowercaseStart(text: String): List<Problem> {
    return listOfNotNull(LOWERCASE_START.find(text)?.let { Problem(Type.LOWERCASE, it.groupValues[1]) })
  }

  private fun sentenceStarts(text: String): Set<Int> {
    return SENTENCE_BREAK.findAll(text).map { it.range.last + 1 }.toSet() + 0
  }

  private fun withoutPossessive(word: String): String {
    return word.replace(POSSESSIVE, "")
  }

  private fun valueOf(number: String): Long {
    return number.replace(",", "").toLong()
  }

  private companion object {
    const val WORDS = "prose/english-words.txt"
    const val SMALL_COUNT = 12L

    /** Capitalised words that are voice, not names. */
    val STYLE_WORDS = setOf(
      "I", "O", "Oh", "Thou", "Thee", "Thy", "Thine", "Ye", "He", "She", "They", "His", "Her", "Their",
      "Lord", "Lady", "Mother", "Father", "Amen", "Here", "Hail", "Glory", "Twas", "Tis",
    )

    /** Titles a model likes to put before a name. */
    val TITLES = setOf("King", "Queen", "Lord", "Lady", "Prince", "Princess", "General", "Elder", "Saint", "Master", "Sir")
    val CONNECTORS = setOf("of", "the")

    const val CAPITAL_WORD = """[A-Z][\w'’-]*"""
    val CAPITALISED = Regex(CAPITAL_WORD)

    // Spaces only, never a line break: a grave stone puts a name on one line and "Born" on the next.
    val PHRASE = Regex("""$CAPITAL_WORD(?:[ \t]+(?:of[ \t]+the|of|the)[ \t]+$CAPITAL_WORD|[ \t]+$CAPITAL_WORD)*""")
    val NUMBER = Regex("""(?<![\w.])\d[\d,]*(?![\w.]\d)""")
    val WORD_LIKE = Regex("""[\w'-]+""")
    val SPACES = Regex("""[ \t]+""")
    val OF = Regex(""" of (?:the )?""")
    val POSSESSIVE = Regex("""['’]s$""")
    val SENTENCE_BREAK = Regex("""[.!?:;\n"“]\s*""")
    val LOWERCASE_START = Regex("""(?:^|[.!?]\s+)([a-z]\w*)""")
  }
}
