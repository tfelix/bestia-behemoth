package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.AttackType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ElementHealTest {

  private val strategies = AttackStrategyFactory(LineOfSightService(), FixedRandom(0f))

  private fun ctx(type: AttackType, element: Element, target: Element): EntityBattleContext {
    return BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(element = element).copy(attackType = type),
      defenderEntity = BattleContextFixture.battleEntity(id = BattleContextFixture.DEFENDER_ID, element = target),
    ) as EntityBattleContext
  }

  @Test
  fun `the table's negative entries heal`() {
    assertTrue(ElementModifier.heals(Element.POISON, Element.UNDEAD))
    assertTrue(ElementModifier.heals(Element.HOLY, Element.HOLY_2))
    assertFalse(ElementModifier.heals(Element.FIRE, Element.EARTH))
    assertEquals(0.25f, ElementModifier.weightOf(Element.POISON, Element.UNDEAD))
  }

  @Test
  fun `a poison shot heals the undead instead of hurting it`() {
    val shot = ctx(AttackType.RANGED_PHYSICAL, Element.POISON, Element.UNDEAD)

    val result = strategies.getAttackStrategy(shot).execute(shot)

    assertIs<Heal>(result)
    assertTrue(result.amount > 0)
  }

  @Test
  fun `a spell of the target's own element heals it`() {
    val spell = ctx(AttackType.MAGIC, Element.SHADOW, Element.SHADOW_4)

    assertIs<Heal>(strategies.magicStrategy.execute(spell))
    assertIs<Heal>(strategies.magicStrategy.land(spell, 100f))
  }

  @Test
  fun `an ordinary element still hurts`() {
    val shot = ctx(AttackType.RANGED_PHYSICAL, Element.FIRE, Element.EARTH)

    assertFalse(strategies.getAttackStrategy(shot).execute(shot) is Heal)
    assertIs<HitDamage>(strategies.magicStrategy.land(ctx(AttackType.MAGIC, Element.FIRE, Element.EARTH), 100f))
  }
}
