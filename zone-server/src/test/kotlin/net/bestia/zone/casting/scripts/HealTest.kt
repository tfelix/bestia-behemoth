package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealTest {

  private val sut = Heal(LineOfSightService())

  @Test
  fun `heal scales with skill level, character level and intelligence`() {
    val amounts = mutableMapOf<Triple<Int, Int, Int>, Int>()

    for (skillLv in listOf(1, 5, 10)) {
      for (charLv in listOf(1, 10, 50, 100)) {
        for (int in listOf(1, 10, 50, 100, 140)) {
          amounts[Triple(skillLv, charLv, int)] = healed(skillLv, charLv, int)
        }
      }
    }

    // Monotonic in each axis independently: a rank, a level or a point of INT must never make a heal worse.
    for ((key, amount) in amounts) {
      val (skillLv, charLv, int) = key
      amounts[Triple(skillLv + 5, charLv, int)]?.let {
        assertTrue(it >= amount, "rank $skillLv->${skillLv + 5} at Lv$charLv/INT$int went $amount -> $it")
      }
      amounts[Triple(skillLv, charLv * 10, int)]?.let {
        assertTrue(it >= amount, "level $charLv->${charLv * 10} at rank $skillLv/INT$int went $amount -> $it")
      }
      amounts[Triple(skillLv, charLv, int * 10)]?.let {
        assertTrue(it >= amount, "INT $int->${int * 10} at rank $skillLv/Lv$charLv went $amount -> $it")
      }
    }
  }

  @Test
  fun `heal follows the ragnarok formula`() {
    // [(50 + 30) / 8] * (4 + 8 * 10) = 10 * 84
    assertEquals(840, healed(skillLevel = 10, level = 50, intelligence = 30))
  }

  @Test
  fun `mana cost grows by 3 per level from 13`() {
    val skill = SkillContextFixture.skill()

    assertEquals(listOf(13, 16, 40), listOf(1, 2, 10).map { sut.costOf(skill, it).manaCost })
  }

  @Test
  fun `an undead foe takes half the heal as holy damage, weighed by its element`() {
    val world = RecordingSkillWorld().apply { foes += BattleContextFixture.DEFENDER_ID }

    // [(50 + 30) / 8] * (4 + 8 * 10) = 840; holy against Undead 1 is 150%, then halved.
    assertNull(sut.execute(undeadCtx(world)))

    assertEquals(listOf(BattleContextFixture.DEFENDER_ID to HitDamage(630)), world.appliedDamage)
    assertEquals(listOf(HitKind.magic(Element.HOLY)), world.appliedKinds)
  }

  @Test
  fun `an undead ally is neither healed nor hurt`() {
    val world = RecordingSkillWorld()

    assertNull(sut.execute(undeadCtx(world)))
    assertTrue(world.appliedDamage.isEmpty())
  }

  private fun undeadCtx(world: RecordingSkillWorld) = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(10),
      attackerEntity = BattleContextFixture.battleEntity(level = 50, intelligence = 30),
      defenderEntity = BattleContextFixture.battleEntity(element = Element.UNDEAD, id = BattleContextFixture.DEFENDER_ID)
    ),
    world
  )

  private fun healed(skillLevel: Int, level: Int, intelligence: Int): Int {
    val ctx = SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(skillLevel),
        attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence)
      )
    )

    return sut.execute(ctx)?.amount ?: error("Heal must always produce a number against an entity")
  }
}
