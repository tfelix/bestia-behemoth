package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WizardBoltsTest {

  private val los = LineOfSightService()

  @Test
  fun `a bolt is MATK raised by a fifth per level, less MDEF`() {
    // The fixture's caster has 15 MATK, the target 5 MDEF: 15 x 120% - 5, and 15 x 200% - 5.
    assertEquals(HitDamage(13), Firebolt(los).execute(ctx(Element.FIRE, level = 1)))
    assertEquals(HitDamage(25), Firebolt(los).execute(ctx(Element.FIRE, level = 5)))
  }

  @Test
  fun `each bolt is weighed by its element against the target's and by the target's resistance`() {
    assertEquals(HitDamage(40), IceBolt(los).execute(ctx(Element.WATER, level = 5, target = Element.FIRE)))
    assertEquals(HitDamage(40), EarthSpike(los).execute(ctx(Element.EARTH, level = 5, target = Element.WIND)))
    assertEquals(HitDamage(10), Firebolt(los).execute(ctx(Element.FIRE, level = 5, target = Element.WATER)))
    assertEquals(HitDamage(10), ThunderBolt(los).execute(ctx(Element.WIND, level = 5, resistance = mapOf(Element.WIND to 50))))
  }

  @Test
  fun `each level casts a fifth of a second faster`() {
    val skill = SkillContextFixture.skill().apply { castTime = 2.0f }

    assertEquals(1.8f, Firebolt(los).costOf(skill, 1).castTimeSeconds, 0.001f)
    assertEquals(1.0f, IceBolt(los).costOf(skill, 5).castTimeSeconds, 0.001f)
  }

  private fun ctx(
    element: Element,
    level: Int,
    target: Element = Element.NORMAL,
    resistance: Map<Element, Int> = emptyMap(),
  ) = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(level = level, element = element),
      defenderEntity = BattleContextFixture.battleEntity(
        id = BattleContextFixture.DEFENDER_ID, element = target, magicDefense = 5, elementResistance = resistance
      ),
    ),
    RecordingSkillWorld(),
    skillLevel = level,
  )
}
