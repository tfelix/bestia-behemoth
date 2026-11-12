package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.mark.HuntersMarks
import net.bestia.zone.battle.mark.MarkedPrey
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HuntersMarkedTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val service = StatusEffectService(definitions, StatusEffectScriptRegistry(listOf(HuntersMarked())))
  private val world = testWorld()

  private val hunter = world.createEntity { }
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

  private fun mark(target: Long, by: Long, level: Int) {
    service.applyEffect(world, target, StatusEffectId.HUNTERS_MARK, level, sourceEntityId = by)
  }
}
