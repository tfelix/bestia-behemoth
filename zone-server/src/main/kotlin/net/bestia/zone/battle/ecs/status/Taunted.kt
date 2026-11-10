package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * A creature provoked into fighting [byEntityId], whoever else is near or hitting it. Put on and taken off by the
 * status that taunts, read by the AI's perception before anything else it would pick.
 */
data class Taunted(val byEntityId: EntityId) : Component
