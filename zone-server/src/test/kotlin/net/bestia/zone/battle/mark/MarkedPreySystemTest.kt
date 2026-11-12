package net.bestia.zone.battle.mark

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkedPreySystemTest {

  private val world = testWorld()
  private val system = MarkedPreySystem()

  private val hunter = world.createEntity { }
  private val prey = world.createEntity {
    world.add(it, Position.fromVec3(Vec3L(4, 5, 0)))
    world.add(it, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.HUNTERS_MARK.id, 1, 30f, hunter))))
  }
  private val marked = MarkedPrey().also { it.preyId = prey }

  init {
    world.add(hunter, marked)
  }

  @Test
  fun `the hunter's map follows the prey`() {
    system.update(world, 1f)

    assertEquals(Vec3L(4, 5, 0), marked.position)
  }

  @Test
  fun `a prey that lost the mark is no longer followed`() {
    world.getOrThrow(prey, StatusEffects::class).removeEffect(StatusEffectId.HUNTERS_MARK.id)

    system.update(world, 1f)

    assertNull(marked.preyId)
    assertNull(marked.position)
  }

  @Test
  fun `a prey that is gone is no longer followed`() {
    world.destroy(prey)

    system.update(world, 1f)

    assertNull(marked.preyId)
  }
}
