package net.bestia.zone.dialog.conversation.smalltalk

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.environment.time.Season
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * The mundane things townsfolk say, and the one knob that decides how often anybody says one.
 *
 * Refuses to load a pool that would leave a trade with nothing to say whatever the weather - see
 * [requireEveryTradeIsCovered]. That is the failure worth a boot check, because it is invisible: a
 * farmer with only rainy-day lines is silent on a clear day, in a season nobody tested, and looks like
 * a working feature rather than a gap.
 */
@Service
class SmallTalkCatalogue(
  private val occupations: OccupationCatalogue,
) {

  private val lines = ArrayList<SmallTalk>()

  private var loadedChance = 0.0

  /** How often a speaker has anything mundane to say at all. The whole boredom control. */
  val chance: Double
    get() {
      return loadedChance
    }

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, SmallTalkYmlDto::class.java)

    require(file.chance in 0.0..1.0) { "$RESOURCE: chance must be a probability, was ${file.chance}" }
    loadedChance = file.chance

    file.lines.forEach { register(it) }

    requireEveryTradeIsCovered()

    LOG.info {
      "Loaded ${lines.size} small-talk lines in ${lines.sumOf { it.variants }} phrasings, " +
        "offered with chance $chance"
    }
  }

  /** The lines true of this speaker right now, in catalogue order. */
  fun eligible(circumstance: Circumstance): List<SmallTalk> {
    return lines.filter { it.holdsFor(circumstance) }
  }

  /**
   * A line by its position, which is also its topic id.
   *
   * Safe only because nothing persists a topic id: the server keeps no conversation state, so
   * reordering the yml can at worst change what an option already on a player's screen resolves to.
   * Anything that ever stores one of these would need a stable id in the file instead.
   */
  fun at(index: Int): SmallTalk? {
    return lines.getOrNull(index)
  }

  fun indexOf(line: SmallTalk): Int {
    return lines.indexOf(line)
  }

  fun all(): List<SmallTalk> {
    return lines
  }

  private fun register(dto: LineDto) {
    require(lines.none { it.key == dto.key }) { "$RESOURCE: '${dto.key}' is declared twice" }

    val unknownTrades = dto.occupations - occupations.ids()
    require(unknownTrades.isEmpty()) {
      "$RESOURCE: '${dto.key}' is gated on ${unknownTrades.joinToString(", ")}, which no occupation has. " +
        "Known: ${occupations.ids().joinToString(", ")}"
    }

    lines += SmallTalk(
      key = dto.key,
      variants = dto.variants,
      occupations = dto.occupations.toSet(),
      kinship = dto.kinship.map { Kinship.valueOf(it) }.toSet(),
      terrain = dto.terrain.map { RegionKind.valueOf(it) }.toSet(),
      season = dto.season.map { Season.valueOf(it) }.toSet(),
      weather = dto.weather.map { WeatherKind.valueOf(it) }.toSet(),
      minAge = dto.minAge,
      maxAge = dto.maxAge,
      walled = dto.walled,
      sacked = dto.sacked,
      minWealth = dto.minWealth,
      maxWealth = dto.maxWealth,
    )
  }

  /**
   * That every trade has at least one line it can always reach.
   *
   * "Always" is the point: a line gated on the coast or on winter is a bonus, and a pool made entirely
   * of bonuses leaves an inland farmer in spring with nothing.
   *
   * It would be easy to let the four seasonal lines satisfy this - between them they cover every day of
   * the year, so nobody is ever strictly without one. That is the fragile version: deleting a single
   * seasonal row would silence a quarter of the calendar rather than failing here.
   */
  private fun requireEveryTradeIsCovered() {
    val uncovered = occupations.ids().filter { trade ->
      lines.none { it.isUnconditional && (it.occupations.isEmpty() || trade in it.occupations) }
    }

    require(uncovered.isEmpty()) {
      "$RESOURCE: ${uncovered.joinToString(", ")} have no line that holds regardless of place and " +
        "season, so they fall silent wherever the conditions happen not to be met"
    }
  }

  private data class SmallTalkYmlDto(
    val chance: Double = 0.0,
    val lines: List<LineDto> = emptyList(),
  )

  private data class LineDto(
    val key: String,
    val variants: Int = 1,
    val occupations: List<String> = emptyList(),
    val kinship: List<String> = emptyList(),
    val terrain: List<String> = emptyList(),
    val season: List<String> = emptyList(),
    val weather: List<String> = emptyList(),
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val walled: Boolean? = null,
    val sacked: Boolean? = null,
    val minWealth: Double? = null,
    val maxWealth: Double? = null,
  )

  private companion object {
    const val RESOURCE = "townsfolk/small-talk.yml"

    val LOG = KotlinLogging.logger { }
  }
}
