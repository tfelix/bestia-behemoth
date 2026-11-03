package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.BattleContextFixture.ATTACKER_ID
import net.bestia.zone.battle.BattleContextFixture.DEFENDER_ID
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.SkillMechanics
import net.bestia.zone.skill.persistence.SkillStatusRider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class SkillMechanicsTest {

  /** Every roll lands in the middle: a hit, never a crit, the same variance on every swing, the lower hit count. */
  private class HalfRandom : Random() {
    override fun next(bits: Int): Int {
      return 1 shl (bits - 1)
    }
  }

  private val physical = BestiaPhysicalSkill(AttackStrategyFactory(LineOfSightService(), HalfRandom()), HalfRandom())
  private val magic = BestiaMagicSkill(LineOfSightService(), HalfRandom())

  @Test
  fun `a flurry adds up its blows and shows each of them`() {
    val single = physical.execute(ctx(SkillMechanics()))!!
    val flurry = physical.execute(ctx(SkillMechanics(hitsMin = 5, hitsMax = 5)))!!

    assertEquals(5, flurry.hits)
    assertEquals(single.amount * 5, flurry.amount)
  }

  @Test
  fun `a piercing spell ignores magic defence`() {
    val guarded = magic.execute(ctx(SkillMechanics(), defenderMagicDefense = 40))!!.amount
    val pierced = magic.execute(ctx(SkillMechanics(ignoreDefense = true), defenderMagicDefense = 40))!!.amount

    assertEquals(guarded + 40, pierced)
  }

  @Test
  fun `a dash rushes the caster up to its target before the blow`() {
    val world = RecordingSkillWorld().putPosition(DEFENDER_ID, Vec3L(8, 0, 0))

    physical.execute(ctx(SkillMechanics(dash = 3), world))

    assertEquals(RecordingSkillWorld.ForcedMove(ATTACKER_ID, Vec3L(8, 0, 0), 3, 1), world.forcedMoves.single())
  }

  @Test
  fun `a landed hit rolls its statuses, pushes and drains`() {
    val world = RecordingSkillWorld()
      .putPosition(ATTACKER_ID, Vec3L(0, 0, 0))
      .putPosition(DEFENDER_ID, Vec3L(1, 0, 0))
    val mechanics = SkillMechanics(
      onHit = listOf(SkillStatusRider(StatusEffectId.STUN.name, chance = 70, durationSeconds = 2.5)),
      knockback = 3,
      lifesteal = 0.5f
    )

    val hit = physical.execute(ctx(mechanics, world))!!

    assertEquals(
      RecordingSkillWorld.Inflicted(DEFENDER_ID, StatusEffectId.STUN.name, 70, 2.5, 3),
      world.inflicted.single()
    )
    assertEquals(RecordingSkillWorld.ForcedMove(DEFENDER_ID, Vec3L(4, 0, 0), 3, 0), world.forcedMoves.single())
    assertEquals(ATTACKER_ID to Heal(hit.amount / 2), world.appliedDamage.single())
  }

  @Test
  fun `a chain jumps to the nearest foe it has not hit yet, weaker each time`() {
    val world = RecordingSkillWorld(entitiesNearby = setOf(DEFENDER_ID, NEAR, FAR))
      .putPosition(DEFENDER_ID, Vec3L(0, 0, 0))
      .putPosition(NEAR, Vec3L(2, 0, 0))
      .putPosition(FAR, Vec3L(3, 0, 0))
    world.foes.addAll(listOf(DEFENDER_ID, NEAR, FAR))

    val first = magic.execute(ctx(SkillMechanics(chainTargets = 2, chainHop = 4), world))!!

    assertEquals(listOf(NEAR, FAR), world.appliedDamage.map { it.first })
    val jump = world.appliedDamage.first().second as HitDamage
    assertTrue(jump.amount < first.amount)
  }

  @Test
  fun `an area carries the skill's statuses to everyone it covers`() {
    val world = RecordingSkillWorld()
    val mechanics = SkillMechanics(onHit = listOf(SkillStatusRider(StatusEffectId.POISON.name, chance = 70)))

    BestiaAreaSkill(LineOfSightService(), HalfRandom()).execute(ctx(mechanics, world, aoeRadius = 2.0))

    val status = world.spawnedAreaEffects.single().effect.riders.statuses.single()
    assertEquals(StatusEffectId.POISON, status.effect)
    assertEquals(70, status.chancePercent)
  }

  @Test
  fun `a zone deals no damage and keeps its status on whoever is inside`() {
    val world = RecordingSkillWorld()
    val mechanics = SkillMechanics(zoneStatus = StatusEffectId.QUAGMIRED.name, areaSeconds = 10f)

    BestiaZoneSkill(LineOfSightService()).execute(ctx(mechanics, world, aoeRadius = 2.0))

    val zone = world.spawnedAreaEffects.single().effect
    assertEquals(0, zone.damagePerTick)
    assertEquals(StatusEffectId.QUAGMIRED, zone.riders.zoneStatus)
    assertEquals(20, zone.remainingTicks)
  }

  private fun ctx(
    mechanics: SkillMechanics,
    world: RecordingSkillWorld = RecordingSkillWorld(),
    aoeRadius: Double? = null,
    defenderMagicDefense: Int? = null,
  ): SkillContext {
    val skill = SkillContextFixture.skill().apply { this.mechanics = mechanics }

    return SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(level = 3, element = Element.FIRE, aoeRadius = aoeRadius, range = 10),
        attackerEntity = BattleContextFixture.battleEntity(level = 30, intelligence = 30, strength = 30),
        defenderEntity = BattleContextFixture.battleEntity(id = DEFENDER_ID, magicDefense = defenderMagicDefense)
      ),
      world,
      skill = skill
    )
  }

  private companion object {
    const val NEAR = 3L
    const val FAR = 4L
  }
}
