package net.bestia.zone.questgen.plan

import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.treasure.TreasureChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import net.bestia.zone.townsfolk.knowledge.ChronicleNames
import net.bestia.zone.townsfolk.knowledge.KnowledgeService
import net.bestia.zone.townsfolk.knowledge.LegendKnowledge
import net.bestia.zone.treasure.Relics
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * Finds the real things of the generated world a quest step can point at. Everything is read from the world
 * tier, so a thing is found whether or not its entity is spawned. Off the tick.
 */
@Component
class GeneratedQuestWorld(
  private val worldService: WorldService,
  private val knowledge: KnowledgeService,
  private val relics: Relics,
  private val items: ItemTemplateRegistry,
) : QuestWorld {

  private class Spot(val id: Long, val at: Vec2d, val settlement: Int = -1)

  /** The markers a quest can point at, by what they hold. One walk over the features of the world. */
  private class Landmarks(
    val cacheByArtifact: Map<Int, Spot>,
    val graveByFigure: Map<Int, Spot>,
    val townPositions: Map<Int, Vec2d>,
  )

  @Volatile
  private var landmarks: Landmarks? = null

  override fun bindings(slot: BindSlot, seed: QuestSeed): List<Binding> {
    if (seed.subject.type != ActorType.ARTIFACT) {
      return emptyList()
    }
    val artifact = seed.subject.index

    return when (slot) {
      BindSlot.RELIC_CACHE -> listOfNotNull(cacheOf(artifact))
      BindSlot.LEGEND_TELLER -> tellersOf(artifact)
      BindSlot.LOSS_WITNESS -> lossWitnessesOf(artifact)
      BindSlot.KEEPER_GRAVE -> listOfNotNull(keeperGraveOf(artifact))
      BindSlot.HOME_TEMPLE -> listOfNotNull(homeTempleOf(artifact))
    }
  }

  override fun argsOf(seed: QuestSeed): Map<String, TextArg> {
    val name = ChronicleNames.artifactOf(chronicle(), seed.subject.index) ?: return emptyMap()

    return mapOf(ARG_ARTIFACT to TextArg.Name(name))
  }

  override fun levelOf(seed: QuestSeed): Int {
    return relics.itemIdOf(seed.subject.index)?.let(items::baseLevelOf) ?: 1
  }

  /** The artifact that lies in the cache, or null for a cache without one. */
  fun artifactIn(cacheId: Long): Int? {
    return landmarks().cacheByArtifact.entries.firstOrNull { it.value.id == cacheId }?.key
  }

  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    landmarks = null
  }

  private fun cacheOf(artifact: Int): Binding? {
    val cache = landmarks().cacheByArtifact[artifact] ?: return null
    val near = standingTowns().minByOrNull { (_, at) -> distance(at, cache.at) }?.key

    return Binding(
      target = WorldTarget.cache(cache.id),
      at = cache.at,
      args = namedPlace(ARG_NEAR, near),
      teaches = listOf(FactRef(FactRef.Subject.ARTIFACT, artifact.toLong(), FactRef.Aspect.PLACE)),
    )
  }

  /** Households that tell the legend of the artifact as it was, in the towns nearest its resting place. */
  private fun tellersOf(artifact: Int): List<Binding> {
    val chronicle = chronicle()
    val restingAt = chronicle.sites.getOrNull(chronicle.artifacts[artifact].restingSite)?.position ?: return emptyList()
    val positions = landmarks().townPositions

    return standingTowns().entries
      .filter { (_, at) -> distance(at, restingAt) <= SettlementLoreService.NEARBY_RANGE }
      .sortedWith(compareBy({ distance(it.value, restingAt) }, { it.key }))
      .mapNotNull { (town, at) ->
        val legend = LegendKnowledge.toldAt(chronicle, town, worldService.record.seed, positions)
          .firstOrNull { it.subject.artifact == artifact && it.truth == LegendKnowledge.Truth.TRUE }
          ?: return@mapNotNull null
        val household = knowledge.of(town).holdersOf(legend.knowledge.topic).firstOrNull() ?: return@mapNotNull null

        Binding(
          target = WorldTarget.townsfolk(TownsfolkIdentity.of(town, household, HEAD_OF_HOUSEHOLD)),
          at = at,
          args = namedPlace(ARG_TOWN, town),
          teaches = listOf(FactRef(FactRef.Subject.ARTIFACT, artifact.toLong(), FactRef.Aspect.NAME)),
        )
      }
      .take(MAX_TELLERS)
  }

  /** Households that remember the event the artifact was lost in, in the towns nearest its resting place. */
  private fun lossWitnessesOf(artifact: Int): List<Binding> {
    val chronicle = chronicle()
    val record = chronicle.artifacts[artifact]
    val lostIn = record.provenance.lastOrNull() ?: return emptyList()
    val restingAt = chronicle.sites.getOrNull(record.restingSite)?.position ?: return emptyList()

    return standingTowns().entries
      .sortedWith(compareBy({ distance(it.value, restingAt) }, { it.key }))
      .take(TOWNS_ASKED)
      .mapNotNull { (town, at) ->
        val household = knowledge.of(town).holdersOf(lostIn).firstOrNull() ?: return@mapNotNull null

        Binding(
          target = WorldTarget.townsfolk(TownsfolkIdentity.of(town, household, HEAD_OF_HOUSEHOLD)),
          at = at,
          args = namedPlace(ARG_TOWN, town),
          teaches = listOf(FactRef.event(lostIn), FactRef(FactRef.Subject.ARTIFACT, artifact.toLong(), FactRef.Aspect.NAME)),
        )
      }
      .take(MAX_TELLERS)
  }

  /** The grave of the last figure in the artifact's story who has one. */
  private fun keeperGraveOf(artifact: Int): Binding? {
    val chronicle = chronicle()
    val graves = landmarks().graveByFigure
    val keeper = chronicle.provenanceOf(artifact).asReversed()
      .flatMap { event -> event.actors.filter { it.type == ActorType.FIGURE } }
      .firstOrNull { it.index in graves }
      ?: return null
    val grave = graves.getValue(keeper.index)
    val name = ChronicleNames.figureOf(chronicle, keeper.index) ?: return null

    return Binding(
      target = WorldTarget.grave(keeper.index),
      at = grave.at,
      args = mapOf(ARG_KEEPER to TextArg.Name(name)) + namedPlace(ARG_GRAVEYARD, grave.settlement),
      teaches = listOf(
        FactRef(FactRef.Subject.FIGURE, keeper.index.toLong(), FactRef.Aspect.NAME),
        FactRef(FactRef.Subject.FIGURE, keeper.index.toLong(), FactRef.Aspect.FATE),
      ),
    )
  }

  private fun homeTempleOf(artifact: Int): Binding? {
    val home = chronicle().artifacts[artifact].homeTemple
    val at = standingTowns()[home] ?: return null
    val relicItem = relics.itemIdOf(artifact) ?: return null

    return Binding(WorldTarget.settlement(home), at, namedPlace(ARG_HOME, home), itemId = relicItem)
  }

  private fun namedPlace(arg: String, settlement: Int?): Map<String, TextArg> {
    val name = settlement?.let { ChronicleNames.placeOf(chronicle(), it) }

    // A text always gets its place, so no line shows a bare placeholder.
    return mapOf(arg to if (name == null) TextArg.Token(UNKNOWN_PLACE) else TextArg.Name(name))
  }

  private fun standingTowns(): Map<Int, Vec2d> {
    val chronicle = chronicle()

    return landmarks().townPositions.filterKeys { chronicle.settlementStood(it, chronicle.presentYear) }
  }

  private fun landmarks(): Landmarks {
    landmarks?.let { return it }

    val generated = worldService.generated
    val markers = generated.world.features.all().filterIsInstance<PointMarker>()
    val caches = markers
      .filter { it.kind == FeatureKind.TREASURE_CACHE && it.attribute(TreasureChannels.ARTIFACT) >= 0 }
      .associate { it.attribute(TreasureChannels.ARTIFACT).toInt() to Spot(it.id.value, it.position) }
    val graves = markers
      .filter { it.kind == FeatureKind.FIXTURE && it.optionalAttribute(FixtureChannels.KIND)?.toInt() == FixtureKind.NAMED_GRAVE.ordinal }
      .associate {
        it.attribute(FixtureChannels.FIGURE).toInt() to
          Spot(it.id.value, it.position, it.optionalAttribute(FixtureChannels.SETTLEMENT)?.toInt() ?: -1)
      }

    return Landmarks(caches, graves, SettlementLoreService.settlementPositions(generated)).also { landmarks = it }
  }

  private fun distance(a: Vec2d, b: Vec2d): Double {
    return hypot(a.x - b.x, a.y - b.y)
  }

  private fun chronicle(): Chronicle {
    return worldService.generated.world.chronicle
  }

  private companion object {
    /** A legend is offered by whoever answers the door. */
    const val HEAD_OF_HOUSEHOLD = 0

    /** More tellers than this only repeat the same towns in the planner's choice. */
    const val MAX_TELLERS = 3

    /** How many of the nearest towns are asked who remembers a loss. A far town would make the walk too long. */
    const val TOWNS_ASKED = 8

    const val UNKNOWN_PLACE = "QUESTGEN_UNKNOWN_PLACE"

    const val ARG_ARTIFACT = "artifact"
    const val ARG_TOWN = "town"
    const val ARG_NEAR = "near"
    const val ARG_KEEPER = "keeper"
    const val ARG_GRAVEYARD = "graveyard"
    const val ARG_HOME = "home"
  }
}
