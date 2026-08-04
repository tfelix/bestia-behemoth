package net.bestia.zone.world

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Vec2d
import org.springframework.stereotype.Service

/**
 * What the people of a town would tell you about: its own history, and what happened nearby.
 *
 * The data half of "an NPC remembers that the mountain erupted". **Nothing calls this yet and nothing speaks** -
 * the delivery half needs an NPC concept, a synced entity, an interaction message and a dialogue system, none of
 * which exist. That is a deliberate scope line and the sweep test is what keeps this side of it from shipping
 * dead, which is the failure `ExposureConfig.comfortHighCelsius` documented for two releases.
 *
 * ### Almost nothing had to be built
 *
 * The chronicle is already in this process's memory. It is never *persisted*, but `WorldService` runs the full
 * pipeline at boot including `HistoryStage`, so `worldService.generated.world.chronicle` is one property access
 * from any bean - and `grep Chronicle zone-server/src` found nothing before this only because nobody had asked.
 * `civ/SettlementSpawnPoints` already reads it, and `MasterSpawnPoint` is a shipped precedent for a
 * chronicle-derived generated string reaching the client.
 *
 * The prose is already rendered, too. `HistoryEvent.detail` is *stored* rather than re-derived precisely so a
 * reader does not need a second copy of every name and relationship, so the sentence a townsperson would say
 * already exists with the real place name in it.
 *
 * ### Two sets of events, and the second is the point
 *
 * A town's **own** events come from `Chronicle.eventsOf`, which matches on the actor list. That finds wars,
 * plagues, walls and foundings.
 *
 * It does **not** find an eruption, and that is not an oversight in the query - it is what
 * `HistorySim.resolveEruptions` made true on purpose. An eruption happens to a *mountain* and carries no
 * settlement actor at all, so the only way to know a town saw one is that its `where` is nearby. Which is also
 * the better story: not "our mountain erupted" but "the mountain took Karth, and you can still walk into what is
 * left of it."
 *
 * ### Memoised in a field, not persisted
 *
 * `WildSpawnerService.dens by lazy` is the precedent and its KDoc is the argument verbatim: a durable copy
 * "would be a table that can go stale against the generator that produced it, in exchange for nothing". The
 * chronicle is deterministically rebuilt each boot with stable indices, so there is nothing for a table to buy.
 * If one is ever added it also needs a `deleteAll()` in `WorldProvisioning.recreate()` - forgetting that is
 * silent corruption, because a new seed means different towns at the same indices.
 *
 * ### The honest limitation
 *
 * Generated prose is **untranslatable by construction**. The static path is the localisation CSVs under
 * `bestia-client/src/Localization`, resolved through Godot `tr()` and baked at build time - so a per-world
 * sentence can never go through it. This is the price the `Names` design already pays knowingly, and it means
 * English-only unless the prose is one day regenerated from per-locale templates instead of pre-rendered
 * `detail` strings.
 */
@Service
class SettlementLoreService(
  private val worldService: WorldService
) {

  /**
   * One thing a townsperson could say, with enough context for a caller to phrase it.
   *
   * [year] and [kind] rather than a rendered "long ago", because how recent something feels is a decision for
   * whoever is speaking - a grandmother and a chronicler place the same year differently - and re-deriving it
   * from the detail string would be impossible.
   */
  data class Memory(
    val year: Int,
    val kind: EventKind,
    val importance: Int,
    /** The pre-rendered sentence, with the real place name already in it. */
    val detail: String,
    /** Whether this happened to somewhere else and was merely witnessed from here. */
    val nearby: Boolean
  )

  /**
   * The [limit] most memorable things known at a settlement, most important first.
   *
   * @param settlementIndex index into `Chronicle.settlements`, which is the same index the `SETTLEMENT` markers
   *   and the economy use - see `HistoryStage.readSites` on why it is dense from zero.
   * @param nearbyRange metres within which something that happened elsewhere is still remembered here. The
   *   default is the ashfall reach, since an eruption is the event this exists to surface.
   */
  fun loreOf(settlementIndex: Int, limit: Int = DEFAULT_LIMIT, nearbyRange: Double = NEARBY_RANGE): List<Memory> =
    loreOf(worldService.generated, settlementIndex, limit, nearbyRange)

  private val positionCache: Map<Int, Vec2d> by lazy { settlementPositions(worldService.generated) }

  /**
   * The query itself, and the reason it is in the companion rather than the bean.
   *
   * Everything here is a pure function of a [GeneratedWorld], so it needs no Spring context and no database -
   * which is what lets `SettlementLoreTest` build a world, ask it what a town remembers and assert on the answer.
   * A test that had to stand up the container to check a pure lookup would be one nobody runs, and this service's
   * whole risk is shipping unexercised.
   */
  companion object {
    /** Enough for a few lines of dialogue without a caller having to trim. */
    const val DEFAULT_LIMIT = 6

    /**
     * Metres within which something elsewhere is still local news.
     *
     * `HistorySim.ASH_REACH`, so a town that survived an ashfall can remember the eruption that caused it. Not
     * shared as a constant because that one is `private` to the simulation and this is a *query* radius rather
     * than the physical reach - they agree today and are allowed to diverge.
     */
    const val NEARBY_RANGE = 18_000.0

    /**
     * Settlement index to position.
     *
     * Through the `SETTLEMENT` markers because `SettlementRecord` carries **no coordinates** - the chronicle is a
     * log, and where a town is is a property of the world tier.
     */
    fun settlementPositions(generated: GeneratedWorld): Map<Int, Vec2d> = generated.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associate { it.attribute(SettlementChannels.INDEX).toInt() to it.position }

    fun loreOf(
      generated: GeneratedWorld,
      settlementIndex: Int,
      limit: Int = DEFAULT_LIMIT,
      nearbyRange: Double = NEARBY_RANGE,
      positions: Map<Int, Vec2d> = settlementPositions(generated)
    ): List<Memory> {
      val chronicle = generated.world.chronicle
      if (settlementIndex !in chronicle.settlements.indices) return emptyList()

      val own = chronicle.eventsOf(Actor(ActorType.SETTLEMENT, settlementIndex))
      val ownIds = own.mapTo(HashSet()) { it.id }

      val at = positions[settlementIndex]
      val nearby = if (at == null) {
        emptyList()
      } else {
        // Excluded by event **id**, so an event that is both this town's own and geographically near cannot
        // appear twice - which every event with a `where` at its own position is.
        chronicle.events.filter { event ->
          val where = event.where
          where != null && where.distanceTo(at) <= nearbyRange && event.id !in ownIds
        }
      }

      return (own.map { it.toMemory(nearby = false) } + nearby.map { it.toMemory(nearby = true) })
        .sortedWith(compareByDescending<Memory> { it.importance }.thenByDescending { it.year })
        .take(limit)
    }

    private fun HistoryEvent.toMemory(nearby: Boolean) =
      Memory(year = year, kind = kind, importance = importance, detail = detail, nearby = nearby)
  }
}
