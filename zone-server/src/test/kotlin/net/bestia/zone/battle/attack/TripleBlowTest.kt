package net.bestia.zone.battle.attack

import io.mockk.mockk
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.breeding.MovableClock
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.skillIdsOf
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Random

class TripleBlowTest {

  private val world = testWorld()
  private val clock = MovableClock(Instant.parse("2026-11-12T12:00:00Z"))
  private val skillIds = skillIdsOf(SkillId.M_TRIPLE_BLOW to TRIPLE_BLOW_ID)

  @Test
  fun `the chance is 3 percent a level`() {
    assertEquals(listOf(3, 15, 30), listOf(1, 5, 10).map { TripleBlow.chancePercent(it) })
  }

  @Test
  fun `only a master who knows Triple Blow rolls it`() {
    val tripleBlow = TripleBlow(skillIds, FixedRandom(0f), clock)

    assertTrue(tripleBlow.rolls(world, brawler(level = 1)))
    assertFalse(tripleBlow.rolls(world, world.createEntity { }))
  }

  @Test
  fun `a roll above the chance stays one blow and opens nothing`() {
    val highestRoll = object : Random() {
      override fun nextInt(bound: Int): Int {
        return bound - 1
      }
    }
    val tripleBlow = TripleBlow(skillIds, highestRoll, clock)
    val brawler = brawler(level = 10)

    assertFalse(tripleBlow.rolls(world, brawler))
    assertFalse(tripleBlow.mayFollowUp(world, brawler))
  }

  @Test
  fun `a Chain Combo may follow a Triple Blow for one and a half seconds`() {
    val tripleBlow = TripleBlow(skillIds, FixedRandom(0f), clock)
    val brawler = brawler(level = 10)

    tripleBlow.rolls(world, brawler)

    clock.advance(Duration.ofMillis(1499))
    assertTrue(tripleBlow.mayFollowUp(world, brawler))
    clock.advance(Duration.ofMillis(1))
    assertFalse(tripleBlow.mayFollowUp(world, brawler))
    assertFalse(tripleBlow.spendOpening(world, brawler))
  }

  @Test
  fun `one Triple Blow allows one Chain Combo`() {
    val tripleBlow = TripleBlow(skillIds, FixedRandom(0f), clock)
    val brawler = brawler(level = 10)

    tripleBlow.rolls(world, brawler)

    assertTrue(tripleBlow.spendOpening(world, brawler))
    assertFalse(tripleBlow.mayFollowUp(world, brawler))
    assertFalse(tripleBlow.spendOpening(world, brawler))
  }

  private fun brawler(level: Int): EntityId {
    return world.createEntity { id -> add(id, KnownSkills(mutableMapOf(TRIPLE_BLOW_ID to level))) }
  }

  private companion object {
    const val TRIPLE_BLOW_ID = 144L
  }
}
