package net.bestia.zone.dialog.conversation

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.dialog.DialogArg
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * How many ways there are to say each line the conversation owns itself.
 *
 * The fourth of these and the last: a memory's phrasings are counted in `dialogue.yml`, a rumour's in
 * `rumours.yml`, a trade's in `occupations.yml`, and everything left - the goodbye, the answer to
 * "what is this place", the shrug when somebody has no news - here. Before this they were single
 * strings, which is why every guard in the world signed off with the same five words.
 *
 * Counts and not the words, for `HistoryLineCatalogue`'s reason: a sentence on the server is a sentence
 * in one language forever, so the phrasings live in the client's translation file and the server's
 * whole interest in them is how many there are to pick between.
 *
 * ### Why the keys are Kotlin's and the counts are the file's
 *
 * A key is spelled at a call site, so it has to be a constant a compiler checks - that is what
 * [ConversationKeys] is for. A count is read by `./gradlew checkDialogDb` as well, and a number
 * repeated in two files is a number that drifts. So the file lists both, and this refuses a boot where
 * it has forgotten one of [ConversationKeys.VARIED].
 */
@Service
class ConversationLineCatalogue {

  private val variants = LinkedHashMap<String, Int>()

  /** The key's position in the file, which is what separates one line's draw from another's. */
  private val topics = HashMap<String, Long>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, DialogueYmlDto::class.java)

    file.conversation.forEach { (key, dto) ->
      require(dto.variants >= 1) {
        "$RESOURCE: '$key' is declared with ${dto.variants} phrasings, so it can never be said"
      }

      val unknown = dto.slots - KNOWN_SLOTS
      require(unknown.isEmpty()) {
        "$RESOURCE: '$key' declares slots ${unknown.joinToString(", ")}, which no provider sends. " +
          "Known: $KNOWN_SLOTS"
      }

      topics[key] = ConversationVariants.FIXED_LINE + variants.size
      variants[key] = dto.variants
    }

    val missing = ConversationKeys.VARIED - variants.keys
    require(missing.isEmpty()) {
      "$RESOURCE says nothing about ${missing.joinToString(", ")}, so nobody could say one. " +
        "Give each a count, or take it out of ConversationKeys.VARIED."
    }

    val unknown = variants.keys - ConversationKeys.VARIED
    require(unknown.isEmpty()) {
      "$RESOURCE counts phrasings for ${unknown.joinToString(", ")}, which no conversation ever says"
    }

    LOG.info { "Loaded phrasing counts for ${variants.size} fixed conversation lines" }
  }

  /**
   * One of [key]'s phrasings, chosen off the speaker's own seed.
   *
   * Keyed on the speaker rather than rolled, for [ConversationVariants]' reason: asking the same person
   * twice has to give the same answer, or the variety reads as a bug.
   */
  fun lineFor(speaker: Speaker, key: String, args: Map<String, DialogArg> = emptyMap()): Line {
    val count = variants[key]
      ?: throw IllegalStateException("No phrasing count for '$key'; the boot check should have caught it")

    val index = ConversationVariants.of(speaker, topics.getValue(key), count)

    return Line("${key}_$index", args)
  }

  /**
   * Only this reader's section. `HistoryLineCatalogue` is the strict one for this file and rejects a
   * top-level key neither of them knows, so repeating `events` and `eras` here would buy nothing.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class DialogueYmlDto(val conversation: Map<String, LineDto> = emptyMap())

  /**
   * [slots] is not read at runtime - the provider that sends a line already knows what it fills in -
   * but declared so `./gradlew checkDialogDb` can refuse a phrasing that reaches for something nobody
   * sends, which would otherwise reach a player as a literal brace.
   */
  private data class LineDto(
    val variants: Int = 1,
    val slots: List<String> = emptyList(),
  )

  private companion object {
    const val RESOURCE = "townsfolk/dialogue.yml"

    /** Every slot a topic provider can fill into one of these lines. Anything else is a typo. */
    val KNOWN_SLOTS = setOf(
      ConversationKeys.SLOT_NAME,
      ConversationKeys.SLOT_TOWN,
      ConversationKeys.SLOT_TRADE,
    )

    val LOG = KotlinLogging.logger { }
  }
}
