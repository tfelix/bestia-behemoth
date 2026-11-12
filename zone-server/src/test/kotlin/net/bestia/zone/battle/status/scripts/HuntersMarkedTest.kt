package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.mark.HuntersMarks
import net.bestia.zone.battle.mark.MarkedPrey
import net.bestia.zone.battle.status.RemovalReason
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
import kotlin.test.assertNull

class HuntersMarkedTest {

  /** Remembers who still sees a hidden body, like the chunk layer would. */
  private class Sight : EntityVisibility {
    val concealed = mutableMapOf<EntityId, Set<AccountId>>()

    override fun conceal(entityId: EntityId, keepVisibleTo: Set<AccountId>) {
      concealed[entityId] = keepVisibleTo
    }

    override fun reveal(entityId: EntityId) {
      concealed.remove(entityId)
    }

    override fun seeConcealed(entityId: EntityId, accountId: AccountId) {
      concealed[entityId]?.let { concealed[entityId] = it + accountId }
    }

    override fun stopSeeingConcealed(entityId: EntityId, accountId: AccountId) {
      concealed[entityId]?.let { concealed[entityId] = it - accountId }
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

  private val sight = Sight()
  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val service = StatusEffectService(definitions, StatusEffectScriptRegistry(listOf(HuntersMarked(sight), Hiding(sight))))
  private val world = testWorld()

  private val hunter = world.createEntity { world.add(it, Account(HUNTER_ACCOUNT)) }
  private val otherHunter = world.createEntity { }
  private val prey = world.createEntity { }

  @Test
  fun `the mark names the prey on the hunter, and only that hunter crits it more often`() {
    mark(prey, by = hunter, level = 5)

    assertEquals(prey, world.get(hunter, MarkedPrey::class)?.preyId)
    assertEquals(15, HuntersMarks.critPercentAgainst(world, hunter, prey))
    assertEquals(0, HuntersMarks.critPercentAgainst(world, otherHunter, prey))
  }

  @Test
  fun `two hunters mark one prey, and taking one mark off leaves the other`() {
    mark(prey, by = hunter, level = 1)
    mark(prey, by = otherHunter, level = 2)

    service.removeEffectFrom(world, prey, StatusEffectId.HUNTERS_MARK.id, hunter, RemovalReason.CANCELLED)

    assertNull(world.get(hunter, MarkedPrey::class)?.preyId)
    assertEquals(0, HuntersMarks.levelOn(world, hunter, prey))
    assertEquals(2, HuntersMarks.levelOn(world, otherHunter, prey))
    assertEquals(prey, world.get(otherHunter, MarkedPrey::class)?.preyId)
  }

  @Test
  fun `a mark that runs out on an old prey leaves the hunter's new prey named`() {
    val newPrey = world.createEntity { }
    mark(prey, by = hunter, level = 1)
    mark(newPrey, by = hunter, level = 1)

    service.removeEffectFrom(world, prey, StatusEffectId.HUNTERS_MARK.id, hunter, RemovalReason.EXPIRED)

    assertEquals(newPrey, world.get(hunter, MarkedPrey::class)?.preyId)
  }

  @Test
  fun `prey that hides after the mark stays in its hunter's sight`() {
    mark(prey, by = hunter, level = 1)

    service.applyEffect(world, prey, StatusEffectId.HIDING, level = 1)

    assertEquals(setOf(HUNTER_ACCOUNT), sight.concealed[prey])
  }

  @Test
  fun `a mark on hidden prey shows it to the hunter until the mark ends`() {
    service.applyEffect(world, prey, StatusEffectId.HIDING, level = 1)

    mark(prey, by = hunter, level = 1)
    assertEquals(setOf(HUNTER_ACCOUNT), sight.concealed[prey])

    service.removeEffectFrom(world, prey, StatusEffectId.HUNTERS_MARK.id, hunter, RemovalReason.EXPIRED)
    assertEquals(emptySet(), sight.concealed[prey])
  }

  private fun mark(target: Long, by: Long, level: Int) {
    service.applyEffect(world, target, StatusEffectId.HUNTERS_MARK, level, sourceEntityId = by)
  }

  private companion object {
    const val HUNTER_ACCOUNT = 77L
  }
}
