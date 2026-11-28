package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A master or an owned bestia gets stamina back by itself only while it rests, and only so far. */
class StaminaRestRuleTest {

  private val world: EcsWorld = testWorld(systems = listOf(StaminaRegenSystem(RegenerationCalculator())))

  /** staminaRegen(100, 60, 80) is 1 + 10 + 10 = 21; rest gets half of it. */
  private val restRegen = 10

  @Test
  fun `a master on its feet gets no stamina back`() {
    val master = master(stamina = 10)

    world.tick(REGEN_DUE)

    assertEquals(10, staminaOf(master))
  }

  @Test
  fun `a resting master gets back half the formula`() {
    val master = master(stamina = 10, StatusEffectId.RESTING)

    world.tick(REGEN_DUE)

    assertEquals(10 + restRegen, staminaOf(master))
  }

  @Test
  fun `rest alone stops at a third of the maximum`() {
    val master = master(stamina = 25, StatusEffectId.RESTING)

    world.tick(REGEN_DUE)
    world.tick(REGEN_DUE)

    assertEquals(30, staminaOf(master))
  }

  @Test
  fun `a worn out master gets nothing back from rest`() {
    val master = master(stamina = 10, StatusEffectId.RESTING, StatusEffectId.WORN_OUT)

    world.tick(REGEN_DUE)

    assertEquals(10, staminaOf(master))
  }

  @Test
  fun `rest does not help while the weather bites`() {
    val master = master(stamina = 10, StatusEffectId.RESTING)
    world.get(master, Stamina::class)!!.exposedAtTick = world.tickCount

    world.tick(REGEN_DUE)

    assertEquals(10, staminaOf(master))
  }

  @Test
  fun `food lifts a master above what rest alone would`() {
    val master = master(stamina = 80, StatusEffectId.RESTING)

    world.tick(REGEN_DUE)

    assertEquals(80, staminaOf(master), "rest never takes stamina away")
  }

  private fun master(stamina: Int, vararg effects: StatusEffectId): EntityId {
    val id = world.createEntity { }
    world.add(id, Master(1L))
    world.add(id, BattleContextFixture.combatStats(vitality = 60, willpower = 80))
    world.add(id, Stamina(current = stamina, max = 100))
    world.add(id, StatusEffects().apply {
      effects.forEach { applyEffect(it.id, StackBehavior.IGNORE_IF_PRESENT, 1, id, Double.POSITIVE_INFINITY, false) }
    })
    return id
  }

  private fun staminaOf(id: EntityId): Int {
    return world.get(id, Stamina::class)!!.current
  }

  private companion object {
    const val REGEN_DUE = 10.1f
  }
}
