package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.GearCoating
import net.bestia.zone.battle.status.scripts.StrippedGear
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.SlotLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class AssassinGearSkillsTest {

  /** Answers each `nextInt` and `nextBoolean` from its own list, in order. */
  private class Rolls(ints: List<Int>, booleans: List<Boolean> = emptyList()) : Random() {
    private val ints = ArrayDeque(ints)
    private val booleans = ArrayDeque(booleans)

    override fun nextInt(bound: Int): Int = ints.removeFirst()

    override fun nextBoolean(): Boolean = booleans.removeFirst()
  }

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `a won roll knocks the picked piece off a master and locks its slot`() {
    world.putMaster(target, masterId = 9L)
    world.wornGear[target] = listOf(SlotLock.ARMOR, SlotLock.FOOTGEAR)

    // 59 is under the 60% of Lv.5; then the second piece.
    strip(level = 5, Rolls(listOf(59, 1)))

    assertEquals(listOf(target to EquipmentSlot.FOOTGEAR), world.stripped)
    assertEquals(listOf(StatusEffectId.FOOTGEAR_STRIPPED.id to 5), world.appliedEffects.map { it.effectId to it.level })
  }

  @Test
  fun `a lost roll takes nothing`() {
    world.wornGear[target] = listOf(SlotLock.ARMOR)

    strip(level = 1, Rolls(listOf(20)))

    assertTrue(world.stripped.isEmpty())
    assertTrue(world.appliedEffects.isEmpty())
    assertEquals(listOf(20, 30, 40, 50, 60), (1..5).map { StripEquipment.chancePercent(it) })
  }

  @Test
  fun `a dual wielder loses both weapons, whichever hand was picked`() {
    world.putMaster(target, masterId = 9L)
    world.wornGear[target] = listOf(SlotLock.WEAPON, SlotLock.SHIELD)
    world.dualWielders += target

    strip(level = 1, Rolls(listOf(0, 1)))

    assertEquals(listOf(EquipmentSlot.RIGHT_HAND, EquipmentSlot.LEFT_HAND), world.stripped.map { it.second })
    assertEquals(
      listOf(StatusEffectId.DISARMED.id, StatusEffectId.SHIELD_STRIPPED.id),
      world.appliedEffects.map { it.effectId }
    )
  }

  @Test
  fun `Equipment Coating makes the strip fail outright, and the caster is told`() {
    world.wornGear[target] = listOf(SlotLock.ARMOR)
    world.effectsOn[target] = mutableSetOf(StatusEffectId.EQUIPMENT_COATING.id)

    strip(level = 5, Rolls(emptyList()))

    assertTrue(world.stripped.isEmpty())
    assertEquals(listOf(OpError.STRIP_GEAR_COATED), world.toldCaster)
  }

  @Test
  fun `a wild bestia with no gear loses a quarter of its ATK or DEF for the slot's time`() {
    strip(level = 3, Rolls(listOf(0), listOf(true)))
    strip(level = 3, Rolls(listOf(0), listOf(false)))

    assertEquals(
      listOf(StatusEffectId.DISARMED.id to 25, StatusEffectId.ARMOR_STRIPPED.id to 25),
      world.appliedEffects.map { it.effectId to it.amount }
    )
  }

  @Test
  fun `a master with nothing on loses nothing`() {
    world.putMaster(target, masterId = 9L)

    strip(level = 5, Rolls(listOf(0)))

    assertTrue(world.appliedEffects.isEmpty())
  }

  @Test
  fun `the weakening of a gearless body cuts ATK on a disarm and DEF on an armor strip`() {
    val disarmed = recalcWith(StatusEffectId.DISARMED, amount = 25)
    val unarmored = recalcWith(StatusEffectId.ARMOR_STRIPPED, amount = 25)
    val stripped = recalcWith(StatusEffectId.DISARMED, amount = 0)

    assertEquals(-25 to 0, disarmed.attackPercent to disarmed.defenseCutPercent)
    assertEquals(0 to 25, unarmored.attackPercent to unarmored.defenseCutPercent)
    assertEquals(0 to 0, stripped.attackPercent to stripped.defenseCutPercent)
  }

  @Test
  fun `Equipment Coating coats the caster and cuts its gear's wear by 20, 60 and 100 percent`() {
    EquipmentCoating().execute(SkillContextFixture.skillCtx(world = world, skillLevel = 2))

    val applied = world.appliedEffects.single()
    assertEquals(Triple(caster, StatusEffectId.EQUIPMENT_COATING.id, 2), Triple(applied.targetEntityId, applied.effectId, applied.level))
    assertEquals(listOf(80, 40, 0), (1..3).map { level ->
      GearCoating.wearKeptPercent(StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.EQUIPMENT_COATING.id, level, 600f))))
    })
    assertEquals(100, GearCoating.wearKeptPercent(null))
  }

  private fun strip(level: Int, random: Random) {
    StripEquipment(LineOfSightService(), random).execute(SkillContextFixture.skillCtx(world = world, skillLevel = level))
  }

  private fun recalcWith(effect: StatusEffectId, amount: Int): StatusValueRecalcContext {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
    StrippedGear().apply(testWorld(), 1L, context, ActiveStatusEffect(effect.id, 1, 5f).apply { this.amount = amount })

    return context
  }
}
