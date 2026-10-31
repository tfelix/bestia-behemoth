package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.BattleContextFixture.DEFENDER_ID
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class BestiaSkillsTest {

  /** Every roll lands in the middle: a hit, never a crit, and the same variance on every swing. */
  private class HalfRandom : Random() {
    override fun next(bits: Int): Int {
      return 1 shl (bits - 1)
    }
  }

  @Test
  fun `a stronger skill and a stronger species both raise physical damage`() {
    val physical = BestiaPhysicalSkill(AttackStrategyFactory(LineOfSightService(), HalfRandom()))

    val plain = physical.execute(ctx(power = 1f))!!.amount
    val powerStrike = physical.execute(ctx(power = 2f))!!.amount
    val strongSpecies = physical.execute(ctx(power = 1f, attackPower = 2f))!!.amount

    assertTrue(powerStrike > plain, "power 2 ($powerStrike) should beat power 1 ($plain)")
    assertTrue(strongSpecies > plain, "attack power 2 ($strongSpecies) should beat 1 ($plain)")
  }

  @Test
  fun `a shot reaches as far as its catalogue range`() {
    val physical = BestiaPhysicalSkill(AttackStrategyFactory(LineOfSightService(), HalfRandom()))
    val shot = SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(range = 7).copy(attackType = AttackType.RANGED_PHYSICAL)
      )
    )

    assertTrue(physical.isCastPossible(shot))
  }

  @Test
  fun `a bolt is weighed by its element against the target`() {
    val magic = BestiaMagicSkill(LineOfSightService())

    val onNormal = magic.execute(ctx(element = Element.FIRE))!!.amount
    val onEarth = magic.execute(ctx(element = Element.FIRE, defenderElement = Element.EARTH))!!.amount

    assertTrue(onEarth > onNormal, "fire on earth ($onEarth) should beat fire on a normal target ($onNormal)")
  }

  @Test
  fun `a dot leaves a patch of its own element and only fire sets the grass alight`() {
    val world = RecordingSkillWorld()
    BestiaDotSkill(LineOfSightService()).execute(ctx(element = Element.POISON, aoeRadius = 1.0, world = world))

    val patch = world.spawnedAreaEffects.single().effect
    assertEquals(Element.POISON, patch.element)
    assertEquals(8, patch.remainingTicks)
    assertTrue(world.ignitions.isEmpty())

    BestiaDotSkill(LineOfSightService()).execute(ctx(element = Element.FIRE, aoeRadius = 1.0, world = world))
    assertEquals(1, world.ignitions.size)
  }

  @Test
  fun `an area skill hits once`() {
    val world = RecordingSkillWorld()
    BestiaAreaSkill(LineOfSightService()).execute(ctx(aoeRadius = 2.0, world = world))

    val burst = world.spawnedAreaEffects.single().effect
    assertEquals(1, burst.remainingTicks)
    assertEquals(2L, burst.radiusTiles)
  }

  @Test
  fun `heal and buff land on the caster even when an enemy was targeted`() {
    val world = RecordingSkillWorld()
    val cast = ctx(world = world)

    assertNull(BestiaHealSkill().execute(cast))
    BestiaBuffSkill().execute(cast)

    val (healed, heal) = world.appliedDamage.single()
    assertEquals(cast.casterId, healed)
    assertTrue(heal is Heal && heal.amount > 0)
    assertEquals(cast.casterId, world.appliedEffects.single().targetEntityId)
    assertEquals(StatusEffectId.EMPOWERED.id, world.appliedEffects.single().effectId)
  }

  @Test
  fun `a debuff weakens the target`() {
    val world = RecordingSkillWorld()
    BestiaDebuffSkill(LineOfSightService()).execute(ctx(world = world))

    val effect = world.appliedEffects.single()
    assertEquals(DEFENDER_ID, effect.targetEntityId)
    assertEquals(StatusEffectId.WEAKENED.id, effect.effectId)
  }

  private fun ctx(
    power: Float = 1f,
    attackPower: Float = 1f,
    element: Element = Element.NORMAL,
    defenderElement: Element = Element.NORMAL,
    aoeRadius: Double? = null,
    world: RecordingSkillWorld = RecordingSkillWorld(),
  ) = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(level = 3, element = element, aoeRadius = aoeRadius).copy(power = power),
      attackerEntity = BattleContextFixture.battleEntity(level = 30, intelligence = 30, strength = 30)
        .copy(attackPower = attackPower),
      defenderEntity = BattleContextFixture.battleEntity(id = DEFENDER_ID, element = defenderElement)
    ),
    world
  )
}
