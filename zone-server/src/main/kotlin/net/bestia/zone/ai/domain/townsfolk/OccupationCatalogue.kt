package net.bestia.zone.ai.domain.townsfolk

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.ai.core.state.HourWindow
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * Every occupation a townsperson may hold, from `resources/townsfolk/occupations.yml`.
 *
 * Behaviour configuration rather than content, so it is held in memory like the AI profiles beside it and
 * not in a table. What it cannot check on its own is whether the trades it names exist and whether every
 * trade has somebody to keep it - both of those need catalogues that are imported later in the boot, so
 * they live in [OccupationCoverage].
 */
@Service
class OccupationCatalogue {

  private val byId = LinkedHashMap<String, Occupation>()

  /** Trades deliberately without anybody to keep them yet. See [OccupationCoverage]. */
  private var unstaffed: Set<String> = emptySet()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, OccupationsYmlDto::class.java)

    file.occupations.forEach { register(it) }
    unstaffed = file.unstaffed.toSet()

    LOG.info { "Loaded ${byId.size} occupations, ${unstaffed.size} trades deliberately unstaffed" }
  }

  fun get(id: String): Occupation? {
    return byId[id]
  }

  fun getOrThrow(id: String): Occupation {
    return get(id) ?: throw IllegalArgumentException("Unknown occupation '$id'; known are ${ids().sorted()}")
  }

  fun all(): Collection<Occupation> {
    return byId.values
  }

  fun ids(): Set<String> {
    return byId.keys
  }

  fun unstaffedTrades(): Set<String> {
    return unstaffed
  }

  private fun register(dto: OccupationDto) {
    val shift = dto.shift?.toWindow()
    val rest = dto.rest?.toWindow() ?: DEFAULT_REST

    // Somebody whose post is open through their own bedtime never gets up from either, and which of the
    // two wins is a priority accident rather than a decision. Cheap to refuse, maddening to watch.
    require(shift == null || !shift.overlaps(rest)) {
      "Occupation '${dto.id}' works $shift and sleeps $rest, which overlap"
    }
    require(byId.put(dto.id, Occupation(dto.id, dto.label ?: dto.id, dto.business, shift, rest)) == null) {
      "Occupation '${dto.id}' is declared twice"
    }
  }

  private data class OccupationsYmlDto(
    val occupations: List<OccupationDto> = emptyList(),
    val unstaffed: List<String> = emptyList(),
  )

  private data class OccupationDto(
    val id: String,
    val label: String? = null,
    /** A `BusinessType.id`. Checked against the catalogue by [OccupationCoverage], not here. */
    val business: String? = null,
    val shift: WindowDto? = null,
    val rest: WindowDto? = null,
  )

  private data class WindowDto(
    @JsonProperty("from") val fromHour: Int,
    @JsonProperty("to") val toHour: Int,
  ) {
    fun toWindow(): HourWindow = HourWindow(fromHour, toHour)
  }

  companion object {
    private const val RESOURCE = "townsfolk/occupations.yml"

    /** What somebody with no reason to keep other hours does. Matches the commoner archetype. */
    val DEFAULT_REST = HourWindow(TownsfolkDomain.BEDTIME_HOUR, TownsfolkDomain.RISE_HOUR)

    private val LOG = KotlinLogging.logger { }
  }
}
