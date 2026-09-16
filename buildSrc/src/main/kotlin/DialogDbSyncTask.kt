import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Cross-checks (and optionally stubs) the server's dialog catalog (`dialogs.yml`) against the Godot
 * client's dialog translation source (`bestia-client/src/Localization/dialogs.csv`).
 *
 * A dialog is the one kind of content where the server holds *no* text at all - it sends an id and
 * the client resolves `DIALOG_<id>_TEXT` itself. That makes the two files impossible to validate
 * against each other at runtime on either side, so it is checked here instead:
 *
 * - every dialog in `dialogs.yml` has a `DIALOG_<id>_TEXT` row (a missing one renders as the literal
 *   key in game),
 * - every placeholder the dialog declares actually appears as `{name}` in the English text, and the
 *   text uses no `{placeholder}` that was never declared - a mismatch either leaves a raw brace on
 *   screen or silently drops a value,
 * - no `DIALOG_*` row is left over from a dialog that no longer exists.
 *
 * Only the `en` column is ever written (via [LocalizationCsv]), and only with a TODO stub for a
 * missing key - real text and every other language column is hand-authored/translated. `_TITLE` rows
 * are optional by design (a dialog may have no heading) and are never created, only checked for
 * orphans.
 */
abstract class DialogDbSyncTask : DefaultTask() {

  @get:InputFile
  abstract val dialogsYml: RegularFileProperty

  @get:InputFile
  abstract val dialogsCsv: RegularFileProperty

  /**
   * The server's conversation catalog, `townsfolk/dialogue.yml`.
   *
   * Optional so the dialog half of this task stands on its own, but in practice always set: the two
   * checks answer the same question - does the client's translation file still agree with what the
   * server thinks it can say - against two catalogs that happen to be separate files.
   */
  @get:InputFile
  @get:Optional
  abstract val conversationYml: RegularFileProperty

  /**
   * The server's small-talk pool, `townsfolk/small-talk.yml`. Optional for the same reason as
   * [conversationYml].
   */
  @get:InputFile
  @get:Optional
  abstract val smallTalkYml: RegularFileProperty

  /** The server's rumour phrasings, `townsfolk/rumours.yml`. Optional for [conversationYml]'s reason. */
  @get:InputFile
  @get:Optional
  abstract val rumoursYml: RegularFileProperty

  /**
   * The server's occupations, `townsfolk/occupations.yml`. Optional for [conversationYml]'s reason.
   *
   * It is the right source for the trade nouns even though it is a file about *people*, because its own
   * contract - enforced at boot by `OccupationCoverage` - is that every trade the generator builds
   * appears in it exactly once, either as somebody's `business` or in `unstaffed`.
   */
  @get:InputFile
  @get:Optional
  abstract val occupationsYml: RegularFileProperty

  /** If true, add TODO stubs for missing keys. If false, only report drift and fail the build on any. */
  @get:Input
  abstract val fix: Property<Boolean>

  private data class DialogDto(
    val id: Int,
    val identifier: String,
    val args: List<String> = emptyList()
  )

  private data class DialogsFile(val dialogs: List<DialogDto> = emptyList())

  private data class HistoryLineDto(val variants: Int = 1, val slots: List<String> = emptyList())

  private data class ConversationFile(
    val events: Map<String, HistoryLineDto> = emptyMap(),
    val eras: List<String> = emptyList(),
  )

  private data class SmallTalkLineDto(val key: String = "")

  private data class SmallTalkFile(val lines: List<SmallTalkLineDto> = emptyList())

  private data class RumourFile(val rumours: Map<String, HistoryLineDto> = emptyMap())

  private data class OccupationDialogDto(val greetings: Int = 1, val trade: Int = 1)

  private data class OccupationDto(
    val id: String = "",
    val business: String? = null,
    val dialog: OccupationDialogDto? = null,
  )

  private data class OccupationsFile(
    val occupations: List<OccupationDto> = emptyList(),
    val unstaffed: List<String> = emptyList(),
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val dialogs = mapper.readValue(dialogsYml.get().asFile, DialogsFile::class.java).dialogs

    val problems = mutableListOf<String>()
    val shouldFix = fix.get()
    val csv = LocalizationCsv.load(dialogsCsv.get().asFile)
    var csvDirty = false

    for (dialog in dialogs) {
      val textKey = textKey(dialog.id)
      val text = csv.get(textKey)

      if (text.isNullOrBlank()) {
        if (shouldFix) {
          csvDirty = csv.upsert(textKey, stubText(dialog)) || csvDirty
          logger.lifecycle("DialogDbSync: added stub $textKey for ${dialog.identifier} - write the real text in dialogs.csv")
        } else {
          problems += "dialogs.csv: '$textKey' is missing (dialog ${dialog.identifier})"
        }
        continue
      }

      val used = PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet()
      val declared = dialog.args.toSet()

      (declared - used).forEach {
        problems += "dialogs.csv: '$textKey' never uses {$it}, but dialogs.yml declares it for ${dialog.identifier}"
      }

      (used - declared).forEach {
        problems += "dialogs.csv: '$textKey' uses {$it}, which ${dialog.identifier} does not declare in dialogs.yml"
      }
    }

    // Only per-dialog keys are owned by the catalog. dialogs.csv also holds UI chrome that belongs to
    // no dialog at all (DIALOG_DEFAULT_TITLE, the fallback window title), which must not be reported
    // as orphaned.
    val knownKeys = dialogs.flatMap { listOf(textKey(it.id), titleKey(it.id)) }.toSet()
    csv.keys()
      .filter { CATALOG_KEY_PATTERN.matches(it) && it !in knownKeys }
      .forEach { problems += "dialogs.csv: '$it' has no corresponding entry in dialogs.yml (orphaned row)" }

    problems += conversationProblems(mapper, csv)
    problems += smallTalkProblems(mapper, csv)
    problems += rumourProblems(mapper, csv)
    problems += eraProblems(mapper, csv)
    problems += tradeProblems(mapper, csv)

    if (shouldFix && csvDirty) {
      dialogsCsv.get().asFile.writeText(csv.render())
    }

    if (!shouldFix && problems.isNotEmpty()) {
      throw GradleException(
        "Dialog drift between dialogs.yml and the client dialogs.csv:\n" +
          problems.joinToString("\n") { "  - $it" } +
          "\nRun './gradlew syncDialogDb' to stub missing keys automatically."
      )
    }
  }

  /**
   * The history phrasings, which are keyed by event-kind *name* rather than by a number.
   *
   * Not the same check as the one above, in one way that matters: a phrasing is allowed to ignore a
   * slot its kind offers - not every sentence about a battle wants to name the year - so only the
   * reverse direction is an error. Using a slot the kind cannot supply renders a literal brace at the
   * player, in whichever locale and on whichever seed happens to hit it.
   */
  private fun conversationProblems(mapper: ObjectMapper, csv: LocalizationCsv): List<String> {
    if (!conversationYml.isPresent) {
      return emptyList()
    }

    val events = mapper.readValue(conversationYml.get().asFile, ConversationFile::class.java).events
    val problems = mutableListOf<String>()
    val expected = HashSet<String>()

    for ((kind, line) in events) {
      val keys = listOf("HISTORY_${kind}_ASK") + (1..line.variants).map { "HISTORY_${kind}_$it" }
      expected += keys

      for (key in keys) {
        val text = csv.get(key)
        if (text.isNullOrBlank()) {
          problems += "dialogs.csv: '$key' is missing, so nobody can mention a $kind"
          continue
        }

        val used = PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet()
        (used - line.slots.toSet()).forEach {
          problems += "dialogs.csv: '$key' uses {$it}, which a $kind event cannot supply"
        }
      }
    }

    csv.keys()
      .filter { HISTORY_KEY_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no event kind or variant in dialogue.yml (orphaned row)" }

    return problems
  }

  /**
   * The small-talk pool, which is two rows per line and no placeholders in either.
   *
   * Slotless on purpose: small talk is a leaf with nothing to fill in, so a placeholder in one of these
   * rows is a translator reaching for an argument the server never sends, and would reach a player as a
   * literal brace.
   */
  private fun smallTalkProblems(mapper: ObjectMapper, csv: LocalizationCsv): List<String> {
    if (!smallTalkYml.isPresent) {
      return emptyList()
    }

    val lines = mapper.readValue(smallTalkYml.get().asFile, SmallTalkFile::class.java).lines
    val problems = mutableListOf<String>()
    val expected = HashSet<String>()

    for (line in lines) {
      for (key in listOf(line.key, line.key + "_ASK")) {
        expected += key

        val text = csv.get(key)
        if (text.isNullOrBlank()) {
          problems += "dialogs.csv: '$key' is missing, so the ${line.key} small talk cannot be said"
          continue
        }

        PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet().forEach {
          problems += "dialogs.csv: '$key' uses {$it}, but small talk is sent without arguments"
        }
      }
    }

    csv.keys()
      .filter { SMALL_TALK_KEY_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no line in small-talk.yml (orphaned row)" }

    return problems
  }

  /**
   * The rumour phrasings, which are keyed by rumour-kind name and shaped exactly like the history ones.
   *
   * Same one-directional slot rule and same reason: a phrasing may ignore a slot its kind offers, but
   * using one no producer sends renders a literal brace at a player.
   */
  private fun rumourProblems(mapper: ObjectMapper, csv: LocalizationCsv): List<String> {
    if (!rumoursYml.isPresent) {
      return emptyList()
    }

    val kinds = mapper.readValue(rumoursYml.get().asFile, RumourFile::class.java).rumours
    val problems = mutableListOf<String>()
    val expected = HashSet<String>()

    for ((kind, line) in kinds) {
      val keys = listOf("RUMOUR_${kind}_ASK") + (1..line.variants).map { "RUMOUR_${kind}_$it" }
      expected += keys

      for (key in keys) {
        val text = csv.get(key)
        if (text.isNullOrBlank()) {
          problems += "dialogs.csv: '$key' is missing, so nobody could mention a $kind"
          continue
        }

        val used = PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet()
        (used - line.slots.toSet()).forEach {
          problems += "dialogs.csv: '$key' uses {$it}, which no producer of a $kind supplies"
        }
      }
    }

    csv.keys()
      .filter { RUMOUR_KEY_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no kind or variant in rumours.yml (orphaned row)" }

    return problems
  }

  /**
   * The era bands, which a history phrasing reaches for as `{era}`.
   *
   * A band is a token in its own right - the nested lookup is what keeps a sentence built out of
   * generated history translatable - and a token with no row renders as the key itself. `dialogue.yml`
   * repeats the band names for exactly this check; the server refuses a boot where its list and the
   * `Era` enum disagree, so checking one checks both.
   */
  private fun eraProblems(mapper: ObjectMapper, csv: LocalizationCsv): List<String> {
    if (!conversationYml.isPresent) {
      return emptyList()
    }

    val expected = mapper.readValue(conversationYml.get().asFile, ConversationFile::class.java).eras
    val problems = mutableListOf<String>()

    for (key in expected) {
      if (csv.get(key).isNullOrBlank()) {
        problems += "dialogs.csv: '$key' is missing, so no memory can say how long ago it was"
      }
    }

    csv.keys()
      .filter { ERA_KEY_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no era in dialogue.yml (orphaned row)" }

    return problems
  }

  /**
   * The trade nouns, one per trade a townsperson could keep.
   *
   * A townsperson names their trade in the one sentence everybody gets asked - "I keep the {trade}
   * here" - and the noun is a token so the sentence stays translatable. A missing row renders as the
   * key itself, which is how `NAME_UNKNOWN` reached players unnoticed; nothing else in this task
   * matches a bare token key.
   *
   * Slotless, for the small-talk rule's reason: a trade noun is one word, not a sentence.
   */
  private fun tradeProblems(mapper: ObjectMapper, csv: LocalizationCsv): List<String> {
    if (!occupationsYml.isPresent) {
      return emptyList()
    }

    val file = mapper.readValue(occupationsYml.get().asFile, OccupationsFile::class.java)
    val problems = mutableListOf<String>()

    // A household's own trade where it keeps one, and the occupation's id where it keeps none.
    val trades = file.occupations.mapNotNull { it.business } + file.unstaffed
    val tradeless = file.occupations.filter { it.business == null }.map { it.id }
    val expected = (trades + tradeless).map { "TRADE_" + it.uppercase() }.toSet()

    for (key in expected) {
      val text = csv.get(key)
      if (text.isNullOrBlank()) {
        problems += "dialogs.csv: '$key' is missing, so nobody keeping that trade can name it"
        continue
      }

      PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet().forEach {
        problems += "dialogs.csv: '$key' uses {$it}, but a trade noun is sent without arguments"
      }
    }

    csv.keys()
      .filter { TRADE_KEY_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no trade in occupations.yml (orphaned row)" }

    problems += occupationLineProblems(file, csv)

    return problems
  }

  /**
   * The lines an occupation has of its own: how it greets somebody, and what it says about its work.
   *
   * Counted per occupation in `occupations.yml`, exactly as a history phrasing is counted per event
   * kind, and checked the same way round: a declared variant with no row renders as the key itself.
   *
   * The trade lines may use `{trade}` and nothing else - that token is what makes one labourer's
   * phrasing read differently for a baker and for a tanner. A greeting may use `{name}`.
   */
  private fun occupationLineProblems(file: OccupationsFile, csv: LocalizationCsv): List<String> {
    val problems = mutableListOf<String>()
    val expected = HashSet<String>()

    for (occupation in file.occupations) {
      val dialog = occupation.dialog ?: OccupationDialogDto()
      val id = occupation.id.uppercase()
      val keys = (1..dialog.greetings).map { "TALK_GREETING_${id}_$it" to setOf("name") } +
        (1..dialog.trade).map { "TALK_TRADE_${id}_$it" to setOf("trade") }

      for ((key, allowed) in keys) {
        expected += key

        val text = csv.get(key)
        if (text.isNullOrBlank()) {
          problems += "dialogs.csv: '$key' is missing, so a ${occupation.id} has nothing to say there"
          continue
        }

        val used = PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toSet()
        (used - allowed).forEach {
          problems += "dialogs.csv: '$key' uses {$it}, which is not sent with that line"
        }
      }
    }

    csv.keys()
      .filter { OCCUPATION_LINE_PATTERN.matches(it) && it !in expected }
      .forEach { problems += "dialogs.csv: '$it' matches no occupation in occupations.yml (orphaned row)" }

    return problems
  }

  private fun stubText(dialog: DialogDto): String {
    val placeholders = dialog.args.joinToString(" ") { "{$it}" }
    return "TODO: write the ${dialog.identifier} dialog text. $placeholders".trim()
  }

  private fun textKey(id: Int) = "DIALOG_${id}_TEXT"

  private fun titleKey(id: Int) = "DIALOG_${id}_TITLE"

  companion object {
    /** A translation key owned by a `dialogs.yml` entry, e.g. `DIALOG_1_TEXT`. */
    private val CATALOG_KEY_PATTERN = Regex("""^DIALOG_\d+_(TEXT|TITLE)$""")

    /** A translation key owned by a `dialogue.yml` entry, e.g. `HISTORY_ERUPTION_1`. */
    private val HISTORY_KEY_PATTERN = Regex("""^HISTORY_[A-Z_]+_(ASK|\d+)$""")

    /** A translation key owned by a `small-talk.yml` line, e.g. `TALK_SMALL_FARMER_BARLEY_ASK`. */
    private val SMALL_TALK_KEY_PATTERN = Regex("""^TALK_SMALL_[A-Z_]+$""")

    /** An era band owned by `dialogue.yml`, e.g. `ERA_LIVING`. */
    private val ERA_KEY_PATTERN = Regex("""^ERA_[A-Z_]+$""")

    /** A translation key owned by a `rumours.yml` entry, e.g. `RUMOUR_BOSS_SLAIN_1`. */
    private val RUMOUR_KEY_PATTERN = Regex("""^RUMOUR_[A-Z_]+_(ASK|\d+)$""")

    /** A trade noun owned by an `occupations.yml` trade, e.g. `TRADE_BAKER`. */
    private val TRADE_KEY_PATTERN = Regex("""^TRADE_[A-Z_]+$""")

    /** A line owned by an `occupations.yml` occupation, e.g. `TALK_GREETING_GUARD_2`. */
    private val OCCUPATION_LINE_PATTERN = Regex("""^TALK_(GREETING|TRADE)_[A-Z_]+_\d+$""")

    /** Matches Godot's `String.format` placeholders, e.g. `{masterName}`. */
    private val PLACEHOLDER_PATTERN = Regex("""\{(\w+)}""")
  }
}
