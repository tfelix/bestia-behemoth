package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ActionGuard
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlStatusEffectsTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val casts = CastCancelService()
  private val scripts = StatusEffectScriptRegistry(
    listOf(Stun(casts), Frozen(casts), Stoning(), Stone(casts), Immobilized(), Feared(casts))
  )
  private val service = StatusEffectService(definitions, scripts)
  private val world = testWorld(systems = listOf(StatusEffectDurationSystem(service, definitions, scripts)))
  private val guard = ActionGuard()

  @Test
  fun `a stun forbids everything and breaks off the cast`() {
    val target = world.createEntity { id -> add(id, Casting(skillId = 1L, skillLevel = 1, targetEntityId = id, targetPosition = null, totalSeconds = 2f)) }

    service.applyEffect(world, target, StatusEffectId.STUN, level = 1)

    assertTrue(ActionLock.entries.all { guard.refuses(world, target, it) })
    assertFalse(world.has(target, Casting::class))
  }

  @Test
  fun `an immobilized body may still fight`() {
    val target = world.createEntity { }

    service.applyEffect(world, target, StatusEffectId.IMMOBILIZED, level = 1)

    assertTrue(guard.refuses(world, target, ActionLock.MOVE))
    assertFalse(guard.refuses(world, target, ActionLock.ATTACK))
  }

  @Test
  fun `fear forbids fighting but not a potion`() {
    val target = world.createEntity { }

    service.applyEffect(world, target, StatusEffectId.FEARED, level = 1)

    assertTrue(guard.refuses(world, target, ActionLock.CAST))
    assertFalse(guard.refuses(world, target, ActionLock.ITEM))
  }

  @Test
  fun `the blow that froze a body does not shatter it, the next one does`() {
    val target = world.createEntity { }
    service.applyEffect(world, target, StatusEffectId.FROZEN, level = 1)

    service.onDamageTaken(world, target, 10)
    assertTrue(hasEffect(target, StatusEffectId.FROZEN))

    world.tick(1f)
    service.onDamageTaken(world, target, 10)
    assertFalse(hasEffect(target, StatusEffectId.FROZEN))
  }

  @Test
  fun `fire does not freeze`() {
    val salamander = world.createEntity { id -> add(id, Nature(Element.FIRE_2, Size.SMALL)) }

    service.applyEffect(world, salamander, StatusEffectId.FROZEN, level = 1)

    assertFalse(hasEffect(salamander, StatusEffectId.FROZEN))
  }

  @Test
  fun `stoning that runs its course turns into stone, which no blow breaks`() {
    val target = world.createEntity { }
    service.applyEffect(world, target, StatusEffectId.STONING, level = 1, durationSeconds = 1.0)

    world.tick(1.5f)
    world.tick(0f)
    assertTrue(hasEffect(target, StatusEffectId.STONE))

    service.onDamageTaken(world, target, 50)
    assertTrue(hasEffect(target, StatusEffectId.STONE))
  }

  @Test
  fun `stone weighs the body as earth and hardens it`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 4f)

    Stone(casts).apply(world, 1L, context, level = 1, sourceEntityId = null)

    assertEquals(Element.EARTH, context.elementOverride)
    assertEquals(90, context.defensePercent)
    assertEquals(0f, context.speed)
  }

  private fun hasEffect(target: EntityId, effect: StatusEffectId): Boolean {
    return world.get(target, StatusEffects::class)?.hasEffect(effect.id) ?: false
  }

  private companion object {
    val BASE = StatusValues(strength = 10, vitality = 10, intelligence = 10, agility = 10, willpower = 10, dexterity = 10)
  }
}
