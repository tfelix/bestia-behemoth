package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.status.StatusEffectId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MagicAttackStrategyTest {

  private val factory = AttackStrategyFactory(LineOfSightService(), FixedRandom(0f))
  private val sut = factory.magicStrategy

  private fun magic(element: Element = Element.NORMAL) =
    BattleContextFixture.attack(element = element).copy(attackType = AttackType.MAGIC)

  private fun ctx(
    element: Element = Element.NORMAL,
    targetElement: Element = Element.NORMAL,
    agility: Int = 10,
    magicDefense: Int = 0,
    hardMagicDefense: Int = 0,
    resistance: Map<Element, Int> = emptyMap(),
    effects: Set<Long> = emptySet(),
  ) = BattleContextFixture.entityCtx(
    attack = magic(element),
    defenderEntity = BattleContextFixture.battleEntity(
      id = BattleContextFixture.DEFENDER_ID,
      agility = agility,
      magicDefense = magicDefense,
      hardMagicDefense = hardMagicDefense,
      element = targetElement,
      elementResistance = resistance,
      activeEffectIds = effects,
    )
  ) as EntityBattleContext

  @Test
  fun `the factory routes magic to the magic strategy`() {
    assertTrue(factory.getAttackStrategy(ctx()) === sut)
  }

  @Test
  fun `a spell never misses however nimble the target, and a wind ward does not turn it`() {
    val damage = sut.execute(ctx(agility = 5_000, effects = setOf(StatusEffectId.SHELTERED.id)))

    assertIs<HitDamage>(damage)
  }

  @Test
  fun `worn MDEF cuts a share and attribute MDEF a flat amount`() {
    val bare = sut.land(ctx(), RAW).amount
    val worn = sut.land(ctx(hardMagicDefense = 50), RAW).amount
    val soft = sut.land(ctx(magicDefense = 30), RAW).amount

    assertEquals(100, bare)
    assertEquals(50, worn)
    assertEquals(70, soft)
  }

  @Test
  fun `the element table and resistance weigh a spell`() {
    // Fire on an earth body is half again in the table; a 50 % fire resistance halves that.
    assertEquals(150, sut.land(ctx(element = Element.FIRE, targetElement = Element.EARTH), RAW).amount)
    assertEquals(
      75,
      sut.land(ctx(element = Element.FIRE, targetElement = Element.EARTH, resistance = mapOf(Element.FIRE to 50)), RAW).amount
    )
  }

  @Test
  fun `a spell that pierces defence ignores both kinds`() {
    assertEquals(100, sut.land(ctx(magicDefense = 30, hardMagicDefense = 50), RAW, ignoreDefense = true).amount)
  }

  @Test
  fun `a spell always does at least one point`() {
    assertEquals(1, sut.land(ctx(magicDefense = 500), RAW).amount)
  }

  @Test
  fun `out of range is refused`() {
    val far = BattleContextFixture.entityCtx(
      attack = magic().copy(range = 2),
      attackerEntity = BattleContextFixture.battleEntity().copy(position = net.bestia.zone.geometry.Vec3L(20, 0, 0)),
    )

    assertFalse(sut.isAttackPossible(far))
  }

  private companion object {
    const val RAW = 100f
  }
}
