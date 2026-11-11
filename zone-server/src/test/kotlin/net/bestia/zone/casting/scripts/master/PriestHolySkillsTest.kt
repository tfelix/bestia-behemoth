package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.BattleEntity
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random

class PriestHolySkillsTest {

  private val world = RecordingSkillWorld()
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Holy Light is MATK raised by a tenth per level and weighed as holy`() {
    // The fixture's caster has 15 MATK, the target 5 MDEF: 22.5 - 5, and 22.5 * 150% - 5 on an Undead.
    assertEquals(HitDamage(17), HolyLight(LineOfSightService()).execute(ctx(level = 5)))
    assertEquals(HitDamage(28), HolyLight(LineOfSightService()).execute(ctx(level = 5, element = Element.UNDEAD)))
  }

  @Test
  fun `Turn Undead destroys a wounded Undead more readily`() {
    val battle = battle(level = 3, element = Element.UNDEAD, health = 50) as EntityBattleContext

    // 40% base, a tenth each of BaseLv, INT and WIL, and 10% for the half of its HP it lost.
    assertEquals(53, MasterTurnUndead.destroyChancePercent(battle, 3))
  }

  @Test
  fun `Turn Undead destroys an Undead outright on a winning roll and strikes as a piercing Holy Light otherwise`() {
    world.natures[target] = Nature(Element.UNDEAD, Size.MEDIUM, kind = BestiaKind.UNDEAD)

    assertEquals(TrueDamage(100), turnUndead(roll = 0).execute(ctx(level = 1, element = Element.UNDEAD)))
    assertEquals(HitDamage(24), turnUndead(roll = 99).execute(ctx(level = 1, element = Element.UNDEAD)))
  }

  @Test
  fun `Turn Undead never destroys a boss or the living`() {
    world.natures[target] = Nature(Element.UNDEAD, Size.MEDIUM, kind = BestiaKind.UNDEAD, boss = true)
    assertEquals(HitDamage(24), turnUndead(roll = 0).execute(ctx(level = 1, element = Element.UNDEAD)))

    world.natures[target] = Nature(Element.NORMAL, Size.MEDIUM, kind = BestiaKind.BEAST)
    assertEquals(HitDamage(16), turnUndead(roll = 0).execute(ctx(level = 1)))
  }

  @Test
  fun `Magnus Exorcismus sends a holy wave every three seconds for fifteen, harder on the unholy`() {
    MagnusExorcismus(LineOfSightService()).execute(groundCtx(level = 3, aoeRadius = 3.0))

    val effect = world.spawnedAreaEffects.single().effect
    // 15 MATK at 145%.
    assertEquals(21, effect.damagePerTick)
    assertEquals(3f, effect.tickIntervalSeconds)
    assertEquals(5, effect.remainingTicks)
    assertEquals(3L, effect.radiusTiles)
    assertEquals(Element.HOLY, effect.element)
    assertEquals(AreaMechanics(weighsElement = true, unholyBonusPercent = 30), effect.mechanics)
  }

  @Test
  fun `Sanctuary heals allies a share of their HP and burns only the unholy for that share of the caster's`() {
    val caster = BattleContextFixture.battleEntity(maxHealth = 400)
    Sanctuary(LineOfSightService()).execute(groundCtx(level = 2, aoeRadius = 2.0, caster = caster))

    val effect = world.spawnedAreaEffects.single().effect
    assertEquals(40, effect.damagePerTick)
    assertEquals(30, effect.remainingTicks)
    assertEquals(10, effect.mechanics.healPercentPerTick)
    assertEquals(true, effect.mechanics.forAllies)
    assertEquals(setOf(BestiaKind.DEMON, BestiaKind.UNDEAD), effect.mechanics.foeKinds)
    assertEquals(setOf(Element.UNDEAD), effect.mechanics.foeElements)
  }

  @Test
  fun `Pneuma shelters everyone in its three by three for twenty seconds`() {
    Pneuma(LineOfSightService()).execute(groundCtx(level = 1, aoeRadius = 1.0))

    val effect = world.spawnedAreaEffects.single().effect
    assertEquals(0, effect.damagePerTick)
    assertEquals(1L, effect.radiusTiles)
    assertEquals(40, effect.remainingTicks)
    assertEquals(AreaMechanics(zoneStatus = StatusEffectId.SHELTERED, zoneStatusForEveryone = true), effect.mechanics)
  }

  private fun groundCtx(level: Int, aoeRadius: Double, caster: BattleEntity = BattleContextFixture.battleEntity()) =
    SkillContextFixture.skillCtx(
      BattleContextFixture.groundCtx(BattleContextFixture.attack(level = level, aoeRadius = aoeRadius), caster),
      world,
      skillLevel = level,
    )

  private fun turnUndead(roll: Int) = MasterTurnUndead(LineOfSightService(), FixedRoll(roll))

  private fun ctx(level: Int, element: Element = Element.NORMAL) =
    SkillContextFixture.skillCtx(battle(level, element), world, skillLevel = level)

  private fun battle(level: Int, element: Element, health: Int = 100) = BattleContextFixture.entityCtx(
    attack = BattleContextFixture.attack(level = level),
    defenderEntity = BattleContextFixture.battleEntity(
      id = target, element = element, magicDefense = 5, maxHealth = 100, health = health
    ),
  )

  private class FixedRoll(private val roll: Int) : Random() {
    override fun nextInt(bound: Int): Int = roll
  }
}
