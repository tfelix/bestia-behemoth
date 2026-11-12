package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.passive.scripts.ArcaneMastery
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.Mindbroken
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WizardArcaneSkillsTest {

  private val world = RecordingSkillWorld()
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Arcane Mastery adds five percent at Lv1 and ten more per level`() {
    assertEquals(listOf(5, 15, 25), (1..3).map { level -> recalc().also { ArcaneMastery().apply(it, level) }.arcaneDamagePercent })
  }

  @Test
  fun `Soul Break is raw mana raised by Arcane Mastery, less MDEF, and drains a share of the target's mana`() {
    // The fixture's caster has 15 MATK, the target 5 MDEF: 15 x 120% - 5, and 15 x 200% x 125% - 5.
    assertEquals(HitDamage(13), SoulBreak(LineOfSightService()).execute(ctx(level = 1)))
    assertEquals(HitDamage(32), SoulBreak(LineOfSightService()).execute(ctx(level = 3, arcane = 25)))

    assertEquals(listOf(target to 5, target to 25), world.manaDrained)
  }

  @Test
  fun `Mindbreak gives the bestia its MATK share, raised by Arcane Mastery`() {
    Mindbreak(LineOfSightService()).execute(ctx(level = 3, arcane = 25))

    val applied = world.appliedEffects.single()
    assertEquals(StatusEffectId.MINDBREAK.id to 3, applied.effectId to applied.level)
    assertEquals(125, applied.amount)
    assertEquals(20, Mindbreak.matkPercent(1, battle(level = 1, arcane = 0)))
  }

  @Test
  fun `Mindbreak is refused on anything but the caster's own bestia`() {
    val ecs = testWorld()
    val caster = ecs.createEntity { id -> add(id, Master(masterId = 7L)) }
    val own = ecs.createEntity { id -> add(id, OwnedBestia(masterId = 7L, playerBestiaId = 1L)) }
    val foreign = ecs.createEntity { id -> add(id, OwnedBestia(masterId = 8L, playerBestiaId = 2L)) }
    val sut = Mindbreak(LineOfSightService())

    assertNull(sut.checkTarget(ecs, caster, own, 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_OWN_BESTIA, sut.checkTarget(ecs, caster, foreign, 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_OWN_BESTIA, sut.checkTarget(ecs, caster, caster, 1, emptyList()))
  }

  @Test
  fun `a mind broken bestia hits harder with magic and loses its soft MDEF`() {
    val base = StatusValues(10, 10, 10, 10, 10, 10)
    val context = recalc(base)

    Mindbroken().apply(testWorld(), 1L, context, ActiveStatusEffect(StatusEffectId.MINDBREAK.id, 3, 60f).apply { amount = 100 })

    val unbroken = CombatValues.ofBase(10, base)
    val broken = CombatValues.fromContext(10, base, context)
    assertEquals(unbroken.derived.matk * 2, broken.derived.matk)
    assertEquals(unbroken.derived.atk, broken.derived.atk)
    assertEquals(0, broken.defense.magicDefense)
    assertEquals(unbroken.defense.defense, broken.defense.defense)
    assertEquals(listOf(20, 60, 100), (1..3).map { Mindbroken.percentOfLevel(it) })
  }

  private fun recalc(base: StatusValues = StatusValues(1, 1, 1, 1, 1, 1)) = StatusValueRecalcContext(base, baseSpeed = 10f)

  private fun ctx(level: Int, arcane: Int = 0) =
    SkillContextFixture.skillCtx(battle(level, arcane), world, skillLevel = level)

  private fun battle(level: Int, arcane: Int) = BattleContextFixture.entityCtx(
    attack = BattleContextFixture.attack(level = level),
    attackerEntity = BattleContextFixture.battleEntity().copy(arcaneDamagePercent = arcane),
    defenderEntity = BattleContextFixture.battleEntity(id = target, magicDefense = 5),
  ) as EntityBattleContext
}
