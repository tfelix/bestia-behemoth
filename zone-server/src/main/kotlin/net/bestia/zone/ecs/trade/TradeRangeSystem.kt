package net.bestia.zone.ecs.trade

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.trade.TradeEndReason
import net.bestia.zone.trade.TradeInterruptedEvent
import net.bestia.zone.trade.TradeService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ends a trade whose two parties have walked apart, or one of whose entities is no longer there.
 *
 * A sweep rather than something hooked onto movement: there is no per-entity "moved" event to subscribe to,
 * and a trade is meant to be face to face for its whole length, not only at the moment it opens. Walking is
 * otherwise free - only the distance matters.
 *
 * Half a second is fast enough that nobody trades across the map and slow enough that this costs nothing;
 * `Vec3L.distance` is two subtractions and a square root.
 */
@SpringComponent
@Order(86)
class TradeRangeSystem(
  private val events: ApplicationEventPublisher,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(0.5f)
  override val reads: ComponentClassSet = setOf(Trading::class, Position::class)

  /**
   * Nothing, deliberately. This only *detects*; the cancellation it asks for releases database reservations
   * and rebuilds two live inventories, which cannot happen on the tick thread, so `TradeService` takes the
   * world lock again from a worker. Declaring `Trading` as written here would claim an ordering that does
   * not exist.
   */
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Trading::class).each { entityId ->
      val trading = get<Trading>()

      // Each trade is seen twice, once from each side. Handling only the lower id keeps one cancellation per
      // trade instead of two racing ones.
      if (entityId > trading.partnerEntityId) {
        return@each
      }

      // Position is read through the world rather than joined into the query: an entity that has lost its
      // position would drop out of a join, and then nobody would be left to notice the trade needs ending.
      val own = world.get(entityId, Position::class)?.toVec3L()
      val partner = world.get(trading.partnerEntityId, Position::class)?.toVec3L()

      if (own == null || partner == null) {
        events.publishEvent(TradeInterruptedEvent(this@TradeRangeSystem, trading.tradeId, TradeEndReason.PARTNER_GONE))
        return@each
      }

      if (own.distance(partner) > MAX_TRADE_RANGE) {
        events.publishEvent(TradeInterruptedEvent(this@TradeRangeSystem, trading.tradeId, TradeEndReason.WALKED_AWAY))
      }
    }
  }

  private companion object {
    /**
     * Shared with [TradeService], deliberately: this is not a second length that happens to match, it is the
     * same rule asked at a different moment - the reach to start a trade and the reach to keep one.
     */
    const val MAX_TRADE_RANGE = TradeService.MAX_TRADE_RANGE
  }
}
