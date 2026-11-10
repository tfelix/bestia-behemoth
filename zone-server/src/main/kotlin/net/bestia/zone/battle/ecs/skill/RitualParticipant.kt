package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * On everyone channeling a ritual. Whatever breaks a channel - damage, a move, a stun - takes it off, and a
 * participant without it has dropped out, see `RitualSystem`.
 */
data class RitualParticipant(val circleId: EntityId) : Component
