package net.bestia.zone.party

import net.bestia.zone.ecs.core.Component

/**
 * Marks a master entity as being part of a party, caching the current roster's account ids so
 * other components (e.g. [net.bestia.zone.ecs.battle.status.Health]/[net.bestia.zone.ecs.battle.status.Mana])
 * can resolve party-wide sync targets with a plain component read instead of a DB lookup from the
 * tick thread. Kept up to date by [PartyService] on every join/leave/kick/disband.
 */
data class PartyMembership(
  val partyId: Long,
  val memberAccountIds: Set<Long>
) : Component
