package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity as having taken damage recently. Blocks HP/Mana regen ([HpRegenSystem],
 * [ManaRegenSystem]) while present; [InCombatSystem] removes it once [remainingSeconds] have
 * elapsed without further damage. Server-side bookkeeping only — deliberately not
 * [net.bestia.zone.ecs.Dirtyable], the client has no need to know about this.
 */
class InCombat(
  var remainingSeconds: Float = TIMEOUT_SECONDS
) : Component {
  companion object {
    const val TIMEOUT_SECONDS = 10f
  }
}
