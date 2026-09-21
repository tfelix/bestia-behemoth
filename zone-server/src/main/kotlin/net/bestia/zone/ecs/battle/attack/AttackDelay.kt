package net.bestia.zone.ecs.battle.attack

import net.bestia.zone.ecs.core.Component

/**
 * How long until this entity may swing again. Armed by `AttackExecutionService` on a swing that resolved and
 * counted down by [AttackSystem]; zero means ready, and so does having no component at all.
 *
 * Server-side bookkeeping only - deliberately not [net.bestia.zone.ecs.core.Dirtyable]. Never removed once
 * created, because a removal defers to the end of the tick and would leave the entity looking on-cooldown for
 * a tick longer than it is.
 */
class AttackDelay(
  var remainingSeconds: Float = 0f
) : Component
