package net.bestia.zone.ecs.trade

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * Marks an entity as being in an open trade, and with whom.
 *
 * Server-only and deliberately not [net.bestia.zone.ecs.core.Dirtyable]: what the trade window shows is
 * mutated off the tick thread by message handlers and is backed by the database, so it is pushed as an
 * explicit [net.bestia.zone.trade.TradeStateSMSG] snapshot rather than mirrored through the dirty sweep.
 * What lives here is only what the tick thread needs - who to measure the distance to.
 *
 * Its presence is also the answer to "are you already trading": one entity, one trade.
 */
data class Trading(
  val tradeId: Long,
  val partnerEntityId: EntityId,
) : Component
