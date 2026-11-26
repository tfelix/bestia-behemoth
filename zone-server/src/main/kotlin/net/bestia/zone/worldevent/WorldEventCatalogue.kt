package net.bestia.zone.worldevent

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.world.mana.ManaTier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/** One event the director may pick, as `world_events.yml` describes it. */
data class WorldEventEntry(
  val id: String,
  val script: String,
  val cost: Double,
  val weight: Double = 1.0,
  val minTier: ManaTier = ManaTier.CALM,
  val minTierCells: Int = 0,
  /** Bestia days before it may start again in the same province. */
  val cooldown: Double = 0.0,
  /** Bestia days it runs before it ends by itself. */
  val lasts: Double = 1.0,
  /** What its contributors share when players end it, by `items.yml` identifier. */
  val loot: List<EventLoot> = emptyList(),
  /** What kind of thing it is, as Observation names it: `mana`, `beasts`, `earth`, `sky` or `people`. */
  val family: String = "mana",
  /** How many times further than a small event Observation feels it. */
  val feltFrom: Double = 1.0,
  /** Whether the boards of its province post it once it runs. */
  val posted: Boolean = true,
)

/** A pile of one item that the contributors of an event split by their share. */
data class EventLoot(val item: String, val amount: Int)

/** The events of `world_events.yml`, each bound to the [WorldEvent] that runs it. The boot fails on a gap. */
@Service
class WorldEventCatalogue(
  events: List<WorldEvent>,
) {

  private val byScript = events.associateBy { it.script }

  private val entries = LinkedHashMap<String, WorldEventEntry>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, FileDto::class.java)

    file.events.forEach { entry ->
      require(entry.id !in entries) { "$RESOURCE names ${entry.id} twice" }
      require(entry.script in byScript) { "$RESOURCE: ${entry.id} runs ${entry.script}, which no WorldEvent implements" }
      require(entry.cost > 0.0) { "$RESOURCE: ${entry.id} costs ${entry.cost}, so it could start every run" }
      entries[entry.id] = entry
    }

    LOG.info { "Loaded ${entries.size} world event(s)" }
  }

  fun all(): Collection<WorldEventEntry> = entries.values

  fun of(id: String): WorldEventEntry? = entries[id]

  fun eventOf(entry: WorldEventEntry): WorldEvent = byScript.getValue(entry.script)

  private data class FileDto(val events: List<WorldEventEntry> = emptyList())

  private companion object {
    const val RESOURCE = "world_events.yml"
    val LOG = KotlinLogging.logger { }
  }
}
