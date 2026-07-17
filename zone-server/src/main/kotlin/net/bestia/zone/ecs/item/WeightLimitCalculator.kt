package net.bestia.zone.ecs.item

import org.springframework.stereotype.Component

/**
 * Pure calculations backing [CarryCapacity]. See the game docs' weight-limit formula:
 * https://docs.bestia-game.net/docs/mechanics/items/#weight-limit
 */
@Component
class WeightLimitCalculator {

  fun computeWeightLimit(strength: Int, vitality: Int, level: Int): Int {
    return strength / 2 + vitality / 5 + 15 + level / 5
  }
}
