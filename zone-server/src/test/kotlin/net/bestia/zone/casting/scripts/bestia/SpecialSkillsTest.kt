package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.BattleContextFixture.ATTACKER_ID
import net.bestia.zone.battle.BattleContextFixture.DEFENDER_ID
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.DetectedEntitiesSMSG
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.skill.persistence.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class SpecialSkillsTest {

  /** Every roll is the lowest: a turn always destroys, a swing always lands. */
  private class LowRandom : Random() {
    override fun next(bits: Int): Int {
      return 0
    }
  }

  @Test
  fun `turn undead destroys an undead outright but never a boss`() {
    val turn = TurnUndead(LineOfSightService(), LowRandom())
    val world = RecordingSkillWorld()

    world.natures[DEFENDER_ID] = Nature(Element.UNDEAD, Size.MEDIUM, kind = BestiaKind.UNDEAD)
    assertEquals(TrueDamage(MAX_HEALTH), turn.execute(ctx(world)))

    world.natures[DEFENDER_ID] = Nature(Element.UNDEAD, Size.MEDIUM, kind = BestiaKind.UNDEAD, boss = true)
    assertFalse(turn.execute(ctx(world)) is TrueDamage)
  }

  @Test
  fun `turn undead cannot be cast on the living`() {
    val world = RecordingSkillWorld()
    world.natures[DEFENDER_ID] = Nature(Element.EARTH, Size.MEDIUM, kind = BestiaKind.BEAST)

    assertFalse(TurnUndead(LineOfSightService()).isCastPossible(ctx(world)))
  }

  @Test
  fun `ultima takes a quarter of the health, a twentieth of a boss's`() {
    val world = RecordingSkillWorld()
    assertEquals(TrueDamage(MAX_HEALTH / 4), Ultima(LineOfSightService()).execute(ctx(world)))

    world.natures[DEFENDER_ID] = Nature(Element.NORMAL, Size.BIG, boss = true)
    assertEquals(TrueDamage(MAX_HEALTH / 20), Ultima(LineOfSightService()).execute(ctx(world)))
  }

  @Test
  fun `ultima fist needs berserk and half the mana, then ends the berserk and seals the pool`() {
    val fist = UltimaFist(AttackStrategyFactory(LineOfSightService(), LowRandom()))
    val world = RecordingSkillWorld().apply { manaFraction = 0.6f; casterMana = 120 }
    assertFalse(fist.isCastPossible(ctx(world)))

    world.effectsOn[ATTACKER_ID] = mutableSetOf(StatusEffectId.BERSERK.id)
    assertTrue(fist.isCastPossible(ctx(world)))

    fist.execute(ctx(world))

    assertEquals(listOf(ATTACKER_ID to StatusEffectId.BERSERK.id), world.removedEffects)
    assertEquals(StatusEffectId.MANA_SEALED.id, world.appliedEffects.single().effectId)
  }

  @Test
  fun `strip takes the gear, or weakens a bestia that wears none`() {
    val world = RecordingSkillWorld()
    Disarm(LineOfSightService()).execute(ctx(world))
    assertEquals(listOf(DEFENDER_ID to EquipmentSlot.RIGHT_HAND), world.stripped)

    world.strippable = false
    StripArmor(LineOfSightService()).execute(ctx(world))
    assertEquals(StatusEffectId.EXPOSED.name, world.inflicted.single().status)
  }

  @Test
  fun `detect marks what it found on its player's map`() {
    val found = DetectedEntitiesSMSG.Detected(9L, Vec3L(3, 3, 0), isMaster = true)
    val world = RecordingSkillWorld().putPosition(ATTACKER_ID, Vec3L(0, 0, 0)).apply { players.add(found) }

    Detect().execute(ctx(world))

    assertEquals(listOf(listOf(found)), world.detections)
  }

  @Test
  fun `an endowment carries the skill's element`() {
    val world = RecordingSkillWorld()
    val skill = SkillContextFixture.skill().apply {
      statusEffect = StatusEffectId.ENDOWED.name
      element = Element.HOLY.name
    }

    BestiaBuffSkill().execute(ctx(world, skill))

    val applied = world.appliedEffects.single()
    assertEquals(StatusEffectId.ENDOWED.id, applied.effectId)
    assertEquals(Element.HOLY.ordinal, applied.amount)
  }

  @Test
  fun `no fresh rage while still fatigued from the last`() {
    val world = RecordingSkillWorld()
    val enrage = SkillContextFixture.skill().apply { statusEffect = StatusEffectId.ENRAGED.name }

    world.effectsOn[ATTACKER_ID] = mutableSetOf(StatusEffectId.FATIGUE.id)

    assertFalse(BestiaBuffSkill().isCastPossible(ctx(world, enrage)))
  }

  private fun ctx(world: RecordingSkillWorld, skill: Skill = SkillContextFixture.skill()): SkillContext {
    return SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(level = 3, element = Element.HOLY, range = 10),
        attackerEntity = BattleContextFixture.battleEntity(level = 30, intelligence = 30, strength = 30),
        defenderEntity = BattleContextFixture.battleEntity(id = DEFENDER_ID, maxHealth = MAX_HEALTH)
      ),
      world,
      skill = skill
    )
  }

  private companion object {
    const val MAX_HEALTH = 4000
  }
}
