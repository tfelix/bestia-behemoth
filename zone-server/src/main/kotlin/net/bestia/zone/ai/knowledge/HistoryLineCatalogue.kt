package net.bestia.zone.ai.knowledge

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.core.EventKind
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * How many ways there are to say each thing the history logged, and with which words filled in.
 *
 * Behaviour configuration rather than content: the phrasings themselves live in the client's
 * translation file, and what is held here is only what the server has to know to *choose* one and fill
 * it. That split is the same one `dialogs.yml` makes, and for the same reason - a sentence on the
 * server is a sentence in one language forever.
 *
 * Refuses to load a catalogue missing an `EventKind`. An unvoiced kind is an entire class of event
 * absent from every conversation in the world, which a player would find long before a developer did.
 */
@Service
class HistoryLineCatalogue {

  private val byKind = LinkedHashMap<EventKind, HistoryLine>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, DialogueYmlDto::class.java)

    // Names only; the bands themselves are Era's. Repeating them in the file is what lets the build
    // check the client has a row for each, and this is what stops the two lists drifting apart.
    require(file.eras == Era.entries.map { it.key }) {
      "$RESOURCE lists eras ${file.eras}, but Era declares ${Era.entries.map { it.key }}"
    }

    file.events.forEach { (kind, dto) -> register(kind, dto) }

    val unvoiced = EventKind.entries - byKind.keys
    require(unvoiced.isEmpty()) {
      "$RESOURCE says nothing about ${unvoiced.joinToString(", ") { it.name }}, so nobody in the world " +
        "could ever mention one. Give each a phrasing, or a reason not to."
    }

    LOG.info { "Loaded phrasings for ${byKind.size} event kinds" }
  }

  fun of(kind: EventKind): HistoryLine {
    return byKind[kind] ?: throw IllegalStateException("No phrasing for $kind; the boot check should have caught it")
  }

  fun all(): Map<EventKind, HistoryLine> {
    return byKind
  }

  private fun register(kind: EventKind, dto: LineDto) {
    require(dto.variants >= 1) { "$kind is declared with ${dto.variants} phrasings, so it can never be said" }

    val unknown = dto.slots - KNOWN_SLOTS
    require(unknown.isEmpty()) {
      "$kind declares slots ${unknown.joinToString(", ")}, which no event can supply. Known: $KNOWN_SLOTS"
    }

    byKind[kind] = HistoryLine(dto.variants, dto.slots.toSet())
  }

  private data class DialogueYmlDto(
    val events: Map<EventKind, LineDto> = emptyMap(),
    val eras: List<String> = emptyList(),
  )

  private data class LineDto(
    val variants: Int = 1,
    val slots: List<String> = emptyList(),
  )

  companion object {
    private const val RESOURCE = "townsfolk/dialogue.yml"

    /** Every slot [HistoryKnowledge] can produce. Anything else is a typo in the catalogue. */
    val KNOWN_SLOTS = setOf(
      HistoryKnowledge.SLOT_PLACE,
      HistoryKnowledge.SLOT_CIV,
      HistoryKnowledge.SLOT_FIGURE,
      HistoryKnowledge.SLOT_ARTIFACT,
      HistoryKnowledge.SLOT_YEAR,
      HistoryKnowledge.SLOT_ERA,
    )

    private val LOG = KotlinLogging.logger { }
  }
}
