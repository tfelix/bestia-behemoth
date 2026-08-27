package net.bestia.zone.ecs.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the anchors [WeightLimitCalculator]'s KDoc claims, on the 100-per-kilogram scale
 * [net.bestia.zone.item.Item.weight] uses.
 *
 * The formula this replaced divided by its constants, and integer division at the low end left a fresh
 * master with 22 units - two kilograms, less than a single lump of ore. Nothing caught it because the class
 * had no test at all, so the numbers below are the point of the file rather than incidental to it.
 */
class WeightLimitCalculatorTest {

  private val calculator = WeightLimitCalculator()

  @Test
  fun `a default level one master carries about twenty five kilograms`() {
    assertEquals(2475, calculator.computeWeightLimit(strength = 10, vitality = 10, level = 1))
  }

  /**
   * The build from the bug report this formula was written for: enough attribute points spent elsewhere that
   * strength never came up. It still has to clear twenty kilograms, or the first player to roll a caster
   * cannot carry their own starting gear.
   */
  @Test
  fun `a low strength level one master still clears twenty kilograms`() {
    assertEquals(2275, calculator.computeWeightLimit(strength = 6, vitality = 10, level = 1))
  }

  /**
   * The KDoc's "~10000 at 100 strength and level 100" is this range, not a point - vitality moves it by up to
   * thirteen kilograms.
   */
  @Test
  fun `a hundred strength at level one hundred is a porters load`() {
    assertEquals(9450, calculator.computeWeightLimit(strength = 100, vitality = 10, level = 100))
    assertEquals(10800, calculator.computeWeightLimit(strength = 100, vitality = 100, level = 100))
  }

  /**
   * Strength has to be the attribute that pays, or investing in it to haul is not a decision. Guards the
   * ordering of the three constants, which a rename or a reordered argument list would otherwise swap
   * silently - every one of them is an `Int` in the same position.
   */
  @Test
  fun `each attribute point and each level is worth a fixed amount`() {
    val base = calculator.computeWeightLimit(strength = 10, vitality = 10, level = 1)

    assertEquals(50, calculator.computeWeightLimit(strength = 11, vitality = 10, level = 1) - base)
    assertEquals(15, calculator.computeWeightLimit(strength = 10, vitality = 11, level = 1) - base)
    assertEquals(25, calculator.computeWeightLimit(strength = 10, vitality = 10, level = 2) - base)
  }
}
