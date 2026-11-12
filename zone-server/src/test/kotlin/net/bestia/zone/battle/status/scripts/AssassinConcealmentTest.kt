package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssassinConcealmentTest {

  private object NoVisibility : EntityVisibility {
    override fun conceal(entityId: EntityId, keepVisibleTo: Set<AccountId>) {}
    override fun reveal(entityId: EntityId) {}
    override fun moved(entityId: EntityId, position: Vec3L) {}
    override fun forgot(entityId: EntityId) {}
    override fun reannounce(accountId: AccountId) {}
    override fun introduce(accountId: AccountId, entityIds: Collection<EntityId>) {}
    override fun observersOf(entityId: EntityId): Set<AccountId> = emptySet()
    override fun drain(): List<EntityVisibility.Delivery> = emptyList()
  }

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(Hiding(NoVisibility), Cloaking(NoVisibility)))
  private val service = StatusEffectService(definitions, scripts)
  private val world = testWorld(systems = listOf(StatusEffectDurationSystem(service, definitions, scripts)))

  @Test
  fun `Hide gives way to a step, an attack or a cast, but not to a potion`() {
    for (action in listOf(EntityAction.MOVED, EntityAction.ATTACKED, EntityAction.CAST)) {
      val body = hidden(StatusEffectId.HIDING)

      service.notifyActed(world, body, action)

      assertFalse(isConcealed(body), "$action should end Hide")
    }

    val drinking = hidden(StatusEffectId.HIDING)
    service.notifyActed(world, drinking, EntityAction.USED_ITEM)
    assertTrue(isConcealed(drinking))
  }

  @Test
  fun `Cloak walks on unseen and takes a blow, but an attack ends it`() {
    val body = hidden(StatusEffectId.CLOAKING)

    service.notifyActed(world, body, EntityAction.MOVED)
    service.onDamageTaken(world, body, 5)
    assertTrue(isConcealed(body))

    service.notifyActed(world, body, EntityAction.ATTACKED)
    assertFalse(isConcealed(body))
  }

  @Test
  fun `Hide at Lv2 drains two and a half mana a second, Cloak at Lv1 six`() {
    val hiding = hidden(StatusEffectId.HIDING, level = 2)
    val cloaked = hidden(StatusEffectId.CLOAKING, level = 1)

    repeat(4) {
      world.tick(1f)
      world.tick(0f)
    }

    assertEquals(MANA - 10, world.getOrThrow(hiding, Mana::class).current)
    assertEquals(MANA - 24, world.getOrThrow(cloaked, Mana::class).current)
    assertEquals(listOf(30, 25, 20, 15, 10), (1..5).map { Hiding.drainTenthsPerSecond(it) })
    assertEquals(listOf(6, 5, 4), (1..3).map { Cloaking.drainPerSecond(it) })
  }

  @Test
  fun `Cloak keeps half the speed at Lv1 and twenty percent more each level`() {
    val speeds = (1..3).map { level ->
      val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
      Cloaking(NoVisibility).apply(testWorld(), 1L, context, ActiveStatusEffect(StatusEffectId.CLOAKING.id, level, 60f))
      context.speed
    }

    assertEquals(listOf(5f, 7f, 9f), speeds)
  }

  private fun hidden(effect: StatusEffectId, level: Int = 1): EntityId {
    val body = world.createEntity { id -> add(id, Mana(MANA, MANA)) }
    service.applyEffect(world, body, effect, level)

    return body
  }

  private fun isConcealed(body: EntityId): Boolean {
    return world.get(body, StatusEffects::class)?.isConcealed() == true
  }

  private companion object {
    const val MANA = 100
  }
}
