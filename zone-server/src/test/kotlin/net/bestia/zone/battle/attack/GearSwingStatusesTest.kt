package net.bestia.zone.battle.attack

import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.Stun
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.util.EntityId
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GearSwingStatusesTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(Stun(mockk(relaxed = true))))
  private val world = testWorld()

  @Test
  fun `gear with a stun chance stuns what a landed swing hits`() {
    val target = fighter()
    val service = StatusEffectService(definitions, scripts, FixedRandom(0.04f))

    GearSwingStatuses(service).onSwingLanded(world, fighter(stunChance = 5), target, HitDamage(5))

    assertTrue(world.get(target, StatusEffects::class)?.hasEffect(StatusEffectId.STUN.id) == true)
  }

  @Test
  fun `gear without a stun chance stuns nothing`() {
    val target = fighter()
    val service = StatusEffectService(definitions, scripts, FixedRandom(0f))

    GearSwingStatuses(service).onSwingLanded(world, fighter(), target, HitDamage(5))

    assertFalse(world.get(target, StatusEffects::class)?.hasEffect(StatusEffectId.STUN.id) == true)
  }

  @Test
  fun `the stun chance of every piece adds up`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 1f)

    ItemStat.STUN_CHANCE.applyTo(context, 5)
    ItemStat.STUN_CHANCE.applyTo(context, 3)

    assertEquals(mapOf(StatusEffectId.STUN to 8), CombatValues.fromContext(1, BASE, context).swingStatusChance)
  }

  private fun fighter(stunChance: Int = 0): EntityId {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 1f)
    if (stunChance > 0) context.addSwingStatusChance(StatusEffectId.STUN, stunChance)

    return world.createEntity { id -> add(id, CombatStats(CombatValues.fromContext(1, BASE, context))) }
  }

  private companion object {
    val BASE = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
  }
}
