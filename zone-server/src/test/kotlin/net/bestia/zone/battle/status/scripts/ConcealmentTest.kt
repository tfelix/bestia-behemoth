package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcealmentTest {

  /** Remembers who is hidden from whom, like the chunk layer would. */
  private class Hiding : EntityVisibility {
    val concealed = mutableMapOf<EntityId, Set<AccountId>>()

    override fun conceal(entityId: EntityId, keepVisibleTo: Set<AccountId>) {
      concealed[entityId] = keepVisibleTo
    }

    override fun reveal(entityId: EntityId) {
      concealed.remove(entityId)
    }

    override fun moved(entityId: EntityId, position: Vec3L) {}
    override fun forgot(entityId: EntityId) {}
    override fun reannounce(accountId: AccountId) {}
    override fun introduce(accountId: AccountId, entityIds: Collection<EntityId>) {}

    override fun observersOf(entityId: EntityId): Set<AccountId> {
      return emptySet()
    }

    override fun drain(): List<EntityVisibility.Delivery> {
      return emptyList()
    }
  }

  private val hiding = Hiding()
  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(Hidden(hiding), Cloaked(hiding)))
  private val service = StatusEffectService(definitions, scripts)
  private val world = testWorld(systems = listOf(StatusEffectDurationSystem(service, definitions, scripts)))

  @Test
  fun `hiding conceals the body from everyone but its own player`() {
    val target = body(mana = 100)

    service.applyEffect(world, target, StatusEffectId.HIDDEN, level = 1)

    assertEquals(setOf(OWNER), hiding.concealed[target])
  }

  @Test
  fun `a cast gives a hidden body away`() {
    val target = body(mana = 100)
    service.applyEffect(world, target, StatusEffectId.HIDDEN, level = 1)

    service.notifyActed(world, target, EntityAction.CAST)

    assertFalse(target in hiding.concealed)
    assertFalse(world.getOrThrow(target, StatusEffects::class).isConcealed())
  }

  @Test
  fun `a blow reveals a cloak`() {
    val target = body(mana = 100)
    service.applyEffect(world, target, StatusEffectId.CLOAKED, level = 1)

    service.onDamageTaken(world, target, 5)

    assertFalse(target in hiding.concealed)
  }

  @Test
  fun `staying hidden costs mana, and it ends when the mana does`() {
    val target = body(mana = 150)
    service.applyEffect(world, target, StatusEffectId.HIDDEN, level = 1)

    world.tick(1f)
    world.tick(0f)
    assertEquals(149, world.getOrThrow(target, Mana::class).current)

    world.getOrThrow(target, Mana::class).current = 0
    world.tick(1f)
    world.tick(0f)
    assertTrue(target !in hiding.concealed)
  }

  private fun body(mana: Int): EntityId {
    return world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, Mana(mana, mana))
    }
  }

  private companion object {
    const val OWNER = 7L
  }
}
