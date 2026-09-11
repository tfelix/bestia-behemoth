package net.bestia.zone.ai.rumour

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import kotlin.math.hypot

/**
 * How something that just happened becomes something a town is talking about.
 *
 * The one entry point for recent news, so the decisions that make it believable - how far word travels,
 * how long it lasts - live together rather than at each call site.
 *
 * News travels at walking pace here as it does in the generator: a posting reaches the settlements
 * inside a strength-scaled radius and nowhere else. A minor thing is heard in one village; something
 * large is heard across a province. Nothing propagates afterwards, because a town retelling a rumour to
 * its neighbours is a graph traversal on the tick thread in exchange for a difference no player can
 * detect.
 */
@Service
class RumourService(
  private val registry: RumourRegistry,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) {

  /**
   * Tells every settlement near [voxelX], [voxelY] that something happened.
   *
   * Voxel coordinates because that is what anything in play holds - an entity position, a chunk, a spell
   * landing. The settlements are stored in metres and the conversion belongs here rather than in every
   * producer.
   *
   * @param strength 0 to 1, how big a thing this was. Scales both the reach and how long it is retold.
   * @return the settlements that heard it
   */
  fun post(
    kind: RumourKind,
    voxelX: Long,
    voxelY: Long,
    strength: Double,
    slots: Map<String, Knowledge.Slot> = emptyMap(),
  ): List<Int> {
    val clamped = strength.coerceIn(0.0, 1.0)
    if (clamped <= 0.0) {
      return emptyList()
    }

    val generated = worldService.generated
    val voxelSize = generated.config.voxelSize
    val at = Vec2d(voxelX * voxelSize, voxelY * voxelSize)

    val reach = MAX_REACH_METRES * clamped
    val today = clock.now().absoluteDay
    val expiry = today + kind.lifetimeDays * clamped

    val encoded = Rumour.Slots.encode(slots)

    val heard = SettlementLoreService.settlementPositions(generated)
      .filter { (_, position) -> hypot(position.x - at.x, position.y - at.y) <= reach }
      .keys
      .sorted()

    heard.forEach { registry.add(it, kind, encoded, today, expiry, clamped) }

    LOG.debug { "$kind at ($voxelX, $voxelY) strength $clamped reached ${heard.size} settlement(s)" }

    return heard
  }

  /**
   * Drops news nobody would bring up any more.
   *
   * Swept rather than filtered on read, because the row is the thing worth removing: a rumour that is
   * merely ignored still costs a row forever, and the table is meant to be bounded by recent play.
   */
  fun forgetExpired() {
    val today = clock.now().absoluteDay

    registry.settlementsWithNews().forEach { settlement ->
      val expired = registry.heardBy(settlement).filter { it.hasExpired(today) }
      registry.forget(settlement, expired)
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /**
     * How far the very largest thing is heard about.
     *
     * `SettlementLoreService.NEARBY_RANGE`, and deliberately the same number: that is the distance at
     * which the chronicle already considers a town to have witnessed something, and two different
     * answers to "near enough to have heard" would show up as a village that knows about the battle in
     * its history but not the one last night.
     */
    const val MAX_REACH_METRES = SettlementLoreService.NEARBY_RANGE
  }
}
