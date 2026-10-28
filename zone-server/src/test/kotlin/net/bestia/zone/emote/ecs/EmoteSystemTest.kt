package net.bestia.zone.emote.ecs

import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmoteSystemTest {

  private val world = testWorld(systems = listOf(EmoteSystem()))

  private fun emoting(kind: AnimationKind): EntityId =
    world.createEntity { id -> add(id, Animation().also { it.startEmote(kind) }) }

  private fun poseOf(id: EntityId) = world.getOrThrow(id, Animation::class).currentAnimation

  @Test
  fun `a once emote ends when its hold runs out`() {
    val id = emoting(AnimationKind.CHEER)

    world.tick(AnimationKind.CHEER.holdSeconds / 2)
    assertEquals(AnimationKind.CHEER, poseOf(id))

    world.tick(AnimationKind.CHEER.holdSeconds)
    assertEquals(AnimationKind.IDLE, poseOf(id))
  }

  @Test
  fun `a lasting emote holds until something interrupts it`() {
    val id = emoting(AnimationKind.SIT)

    repeat(10) { world.tick(1f) }
    assertEquals(AnimationKind.SIT, poseOf(id))

    world.add(id, Path(mutableListOf(Vec3L(1, 0, 0))))
    world.tick(0.05f)
    assertEquals(AnimationKind.IDLE, poseOf(id))
  }

  @Test
  fun `casting and dying stand the entity up`() {
    val caster = emoting(AnimationKind.LIE)
    world.add(caster, Casting(skillId = 1L, skillLevel = 1, targetEntityId = 0L, targetPosition = null, totalSeconds = 1f))
    val corpse = emoting(AnimationKind.SIT)
    world.add(corpse, Dead())

    world.tick(0.05f)

    assertEquals(AnimationKind.IDLE, poseOf(caster))
    assertEquals(AnimationKind.IDLE, poseOf(corpse))
  }

  @Test
  fun `the AI's sleep is not an emote and is left alone`() {
    val sleeper = world.createEntity { id -> add(id, Animation(AnimationKind.SLEEP)) }
    world.add(sleeper, Path(mutableListOf(Vec3L(1, 0, 0))))

    world.tick(5f)

    assertEquals(AnimationKind.SLEEP, poseOf(sleeper))
  }
}
