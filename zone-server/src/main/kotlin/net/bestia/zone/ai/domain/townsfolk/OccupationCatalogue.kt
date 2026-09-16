package net.bestia.zone.ai.domain.townsfolk

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.knowledge.KnowledgeProfile
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
    // An evening is the gap between the two, and TownsfolkDomain builds one per call rather than at boot -
    // so a trade that went straight from its post to its bed would throw in the middle of a tick instead.
    require(shift == null || shift.toHour != rest.fromHour) {
      "Occupation '${dto.id}' goes straight from $shift to $rest, so it has no evening to spend"
    }
    val occupation = Occupation(
      id = dto.id,
      label = dto.label ?: dto.id,
      businessType = dto.business,
      shift = shift,
      rest = rest,
      holdsGround = dto.holdsGround,
      knowledge = dto.knowledge?.toProfile() ?: KnowledgeProfile.ORDINARY,
    )
    require(byId.put(dto.id, occupation) == null) {
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
    @JsonProperty("holds-ground") val holdsGround: Boolean = false,
    val knowledge: KnowledgeDto? = null,
  )

  private data class WindowDto(
    @JsonProperty("from") val fromHour: Int,
    @JsonProperty("to") val toHour: Int,
  ) {
    fun toWindow(): HourWindow = HourWindow(fromHour, toHour)
  }

  /**
   * An unknown `EventKind` name fails the boot here, through Jackson's own enum binding - which is the
   * check worth having, since a silently dropped interest reads exactly like a trade nobody weighted.
   */
  private data class KnowledgeDto(
    val curiosity: Double? = null,
    val interests: List<EventKind> = emptyList(),
  ) {
    fun toProfile(): KnowledgeProfile {
      return KnowledgeProfile(
        curiosity = curiosity ?: KnowledgeProfile.ORDINARY.curiosity,
        interests = interests.toSet(),
      )
    }
  }

  companion object {
    private const val RESOURCE = "townsfolk/occupations.yml"

    /** What somebody with no reason to keep other hours does. Matches the commoner archetype. */
    val DEFAULT_REST = HourWindow(TownsfolkDomain.BEDTIME_HOUR, TownsfolkDomain.RISE_HOUR)

    private val LOG = KotlinLogging.logger { }
  }
}
