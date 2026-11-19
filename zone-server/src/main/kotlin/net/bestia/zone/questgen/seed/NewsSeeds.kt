package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.townsfolk.persistence.Rumour
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourRegistry
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * Big news that stopped at the edge of its reach: one town talks of it, a town not far away has not heard.
 * News does not travel on by itself, so somebody has to carry it. One seed per piece of news, for the shortest
 * way it can go next.
 */
@Component
class NewsSeeds(
  private val world: WorldView,
  private val rumours: RumourRegistry,
  private val worldService: WorldService,
  private val clock: BestiaClock,
  private val config: QuestgenConfig,
) : QuestSeedSource {

  private class Way(val rumour: Rumour, val to: Int, val metres: Double)

  override fun seeds(): List<QuestSeed> {
    val generated = worldService.generated
    val chronicle = generated.world.chronicle
    val towns = SettlementLoreService.settlementPositions(generated)
      .filterKeys { chronicle.settlementStood(it, chronicle.presentYear) }

    return seedsAmong(towns, clock.now().absoluteDay)
  }

  /** @param towns the standing towns and where they are */
  internal fun seedsAmong(towns: Map<Int, Vec2d>, today: Double): List<QuestSeed> {
    // What the towns have heard is kept on the tick.
    return world.read {
      rumours.settlementsWithNews().filter { it in towns }.sorted()
        .flatMap { rumours.heardBy(it) }
        .filter { it.kind in CARRIED && it.strength >= config.newsMinStrength && !it.hasExpired(today) }
        .groupBy { it.kind to it.slots }
        .values
        .mapNotNull { shortestWayOf(it, towns) }
        // A town is told one thing at a time.
        .distinctBy { it.to }
        .map { seedOf(it) }
    }
  }

  private fun shortestWayOf(sameNews: List<Rumour>, towns: Map<Int, Vec2d>): Way? {
    val heard = sameNews.map { it.settlement }.toSet()

    return sameNews
      .flatMap { rumour ->
        val from = towns.getValue(rumour.settlement)
        (towns - heard).map { (to, at) -> Way(rumour, to, hypot(at.x - from.x, at.y - from.y)) }
      }
      .filter { it.metres <= config.newsReachMetres }
      .minWithOrNull(compareBy({ it.metres }, { it.rumour.id }, { it.to }))
  }

  private fun seedOf(way: Way): QuestSeed {
    return QuestSeed(
      QuestSeed.Kind.NEWS,
      Actor(ActorType.SETTLEMENT, way.to),
      others = listOf(Actor(ActorType.SETTLEMENT, way.rumour.settlement)),
      uniqueness = QuestSeed.Uniqueness.UNIQUE,
      rumourId = way.rumour.id,
    )
  }

  private companion object {
    /** What a town would send somebody for. A misdeed is the business of the town that saw it. */
    val CARRIED = setOf(RumourKind.BOSS_SLAIN, RumourKind.TOWN_FELL)
  }
}
