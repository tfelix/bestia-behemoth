package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pinned against Ragnarok Online's published numbers rather than against this implementation, so the model
 * stays recognisable: ASPD 150 is one swing a second and ASPD 190 is five.
 */
class AttackSpeedTest {

  @Test
  fun `a motionless attacker reads the ASPD its motion says`() {
    assertEquals(150, AttackSpeed.aspd(500))
    assertEquals(190, AttackSpeed.aspd(AttackSpeed.MIN_MOTION_MS))
    assertEquals(0, AttackSpeed.aspd(AttackSpeed.ZERO_ASPD_MOTION_MS))
  }

  @Test
  fun `ASPD 150 is one swing a second and ASPD 190 is five`() {
    assertEquals(1f, AttackSpeed.delaySeconds(500, stats(agility = 0, dexterity = 0)))
    assertEquals(0.2f, AttackSpeed.delaySeconds(100, stats(agility = 0, dexterity = 0)))
  }

  @Test
  fun `agility counts four times what dexterity does`() {
    // rAthena's pre-renewal `amotion -= amotion * (4*AGI + DEX) / 1000`.
    assertEquals(665, AttackSpeed.motionMs(700, stats(agility = 10, dexterity = 10)))
    assertEquals(637, AttackSpeed.motionMs(700, stats(agility = 20, dexterity = 10)))
    assertEquals(658, AttackSpeed.motionMs(700, stats(agility = 10, dexterity = 20)))
  }

  @Test
  fun `no amount of agility beats the cap`() {
    assertEquals(AttackSpeed.MIN_MOTION_MS, AttackSpeed.motionMs(700, stats(agility = 200, dexterity = 100)))
    assertEquals(190, AttackSpeed.aspd(AttackSpeed.motionMs(700, stats(agility = 200, dexterity = 100))))
  }

  @Test
  fun `a fresh master swings a little under once a second`() {
    val delay = AttackSpeed.delaySeconds(AttackSpeed.BARE_HANDED_MOTION_MS, stats(agility = 10, dexterity = 10))

    assertEquals(1.33f, delay)
  }

  private fun stats(agility: Int, dexterity: Int): StatusValues {
    return StatusValues(
      strength = 10, vitality = 10, intelligence = 10, willpower = 10,
      agility = agility, dexterity = dexterity
    )
  }
}
