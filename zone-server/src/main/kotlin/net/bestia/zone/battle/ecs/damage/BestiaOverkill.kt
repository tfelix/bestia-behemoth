package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.config.WorldRulesConfig
import org.springframework.stereotype.Component

/** When a killing blow loses an owned bestia for good: it dealt at least a share of the bestia's maximum HP too much. */
@Component
class BestiaOverkill(private val rules: WorldRulesConfig) {

  fun losesForGood(healthBefore: Int, damage: Int, maxHealth: Int): Boolean {
    val overkill = damage - healthBefore

    return overkill >= maxHealth * rules.bestiaLossOverkillShare
  }
}
