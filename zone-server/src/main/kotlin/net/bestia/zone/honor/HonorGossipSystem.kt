package net.bestia.zone.honor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.honor.persistence.LocalHonor
import net.bestia.zone.honor.persistence.LocalHonor.Scope
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Moves what witnesses saw into what their town thinks, a Bestia day later. A town that will never forget a
 * Master warns its neighbours with a rumour that names them, and each neighbour that hears it thinks less of
 * them, less the further away it is.
 *
 * Touches no component: the rows live in [LocalHonorRegistry].
 */
@Component
class HonorGossipSystem(
  catalog: HonorCatalog,
  private val honor: HonorService,
  private val localHonor: LocalHonorRegistry,
  private val rumours: RumourService,
  private val sites: SettlementSiteIndex,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC(),
) : System {
  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(60f)

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  private val gossip = catalog.rules.gossip

  override fun update(world: World, deltaTime: Float) {
    val toldBefore = clock.instant().minus(gossip.delay)

    localHonor.all(Scope.GRUDGE)
      .filter { it.untold > 0 && it.lastLossAt?.isBefore(toldBefore) == true }
      .forEach { tell(it) }
  }

  private fun tell(grudge: LocalHonor) {
    val settlement = TownsfolkIdentity.settlementOf(grudge.holder)
    val forgaveBefore = !honor.isRememberedForever(grudge.masterId, settlement)

    honor.changeSettlement(grudge.masterId, grudge.masterName, settlement, -(grudge.untold * gossip.share).roundToInt())
    localHonor.told(grudge)

    if (forgaveBefore && honor.isRememberedForever(grudge.masterId, settlement)) {
      warnNeighbours(grudge.masterId, grudge.masterName, settlement)
    }
  }

  private fun warnNeighbours(masterId: Long, masterName: String, origin: Int) {
    val centre = sites.siteOf(origin)?.centre ?: return
    val metres = worldService.config.voxelSize

    val heard = rumours.post(
      kind = RumourKind.MISDEED_SEEN,
      voxelX = (centre.x / metres).toLong(),
      voxelY = (centre.y / metres).toLong(),
      strength = 1.0,
      slots = mapOf(NAME_SLOT to Knowledge.Slot.Name(masterName)),
    )

    for (town in heard - origin) {
      val there = sites.siteOf(town)?.centre ?: continue
      val nearness = 1.0 - hypot(there.x - centre.x, there.y - centre.y) / SettlementLoreService.NEARBY_RANGE
      honor.changeSettlement(masterId, masterName, town, -(gossip.rumourLoss * nearness.coerceIn(0.0, 1.0)).roundToInt())
    }

    LOG.info { "Settlement $origin will not forget master $masterId; ${heard.size} town(s) heard of it" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Matches `slots: [name]` for `MISDEED_SEEN` in `townsfolk/rumours.yml`. */
    const val NAME_SLOT = "name"
  }
}
