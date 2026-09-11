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

  private data class ConversationFile(val events: Map<String, HistoryLineDto> = emptyMap())

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

    /** Matches Godot's `String.format` placeholders, e.g. `{masterName}`. */
    private val PLACEHOLDER_PATTERN = Regex("""\{(\w+)}""")
  }
}
