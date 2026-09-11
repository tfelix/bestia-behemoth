package net.bestia.zone.ai.rumour

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * How many ways there are to say each kind of recent news.
 *
 * `HistoryLineCatalogue`'s twin, and separate from it rather than a second section of one file: the two
 * answer the same question about different vocabularies, and a kind in the wrong half would load
 * silently. Behaviour configuration only - the phrasings themselves are in the client's translation
 * file, because a sentence on the server is a sentence in one language forever.
 *
 * Refuses to load a catalogue missing a [RumourKind]. A kind nobody can mention is news that arrives,
 * ages and expires without a single person ever bringing it up.
 */
@Service
class RumourLineCatalogue {

  private val byKind = LinkedHashMap<RumourKind, RumourLine>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, RumoursYmlDto::class.java)

    file.rumours.forEach { (kind, dto) -> register(kind, dto) }

    val unvoiced = RumourKind.entries - byKind.keys
    require(unvoiced.isEmpty()) {
      "$RESOURCE says nothing about ${unvoiced.joinToString(", ") { it.name }}, so news of it would " +
        "arrive and expire without anybody mentioning it."
    }

    LOG.info { "Loaded phrasings for ${byKind.size} rumour kind(s)" }
  }

  fun of(kind: RumourKind): RumourLine {
    return byKind[kind] ?: throw IllegalStateException("No phrasing for $kind; the boot check should have caught it")
  }

  fun all(): Map<RumourKind, RumourLine> {
    return byKind
  }

  private fun register(kind: RumourKind, dto: LineDto) {
    require(dto.variants >= 1) { "$kind is declared with ${dto.variants} phrasings, so it can never be said" }

    byKind[kind] = RumourLine(dto.variants, dto.slots.toSet())
  }

  private data class RumoursYmlDto(val rumours: Map<RumourKind, LineDto> = emptyMap())

  private data class LineDto(
    val variants: Int = 1,
    val slots: List<String> = emptyList(),
  )

  private companion object {
    const val RESOURCE = "townsfolk/rumours.yml"

    val LOG = KotlinLogging.logger { }
  }
}
