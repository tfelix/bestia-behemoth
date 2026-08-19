package net.bestia.zone.ecs.battle.skill

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastingSystemTest {

  private val skillExecutionService = mockk<SkillExecutionService>(relaxed = true)
  private val sut = CastingSystem(skillExecutionService)
  private val world: World = testWorld(systems = listOf(sut))

  private fun castingEntity(castTime: Float, target: EntityId = 99L): EntityId {
    return world.createEntity { id ->
      add(
        id, Casting(
          skillId = SKILL_ID,
          skillLevel = 3,
          targetEntityId = target,
          targetPosition = null,
          totalSeconds = castTime
        )
      )
    }
  }

  @Test
  fun `cast still running only counts down`() {
    val id = castingEntity(castTime = 2f)

    world.tick(0.5f)

    val casting = world.get(id, Casting::class)
    assertNotNull(casting)
    assertEquals(1.5f, casting!!.remainingSeconds, TOLERANCE)
    verify(exactly = 0) { skillExecutionService.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `elapsed cast removes the component and executes the skill`() {
    val id = castingEntity(castTime = 1f)

    world.tick(1.5f)

    assertFalse(world.has(id, Casting::class), "Casting component should be gone once the cast elapsed")
    verify(exactly = 1) {
      skillExecutionService.execute(
        world = world,
        casterId = id,
        skillId = SKILL_ID,
        skillLevel = 3,
        targetEntityId = 99L,
        targetPosition = null
      )
    }
  }

  @Test
  fun `cast cancelled before it elapses never executes`() {
    val id = castingEntity(castTime = 2f)

    world.tick(0.5f)
    world.remove(id, Casting::class)
    world.tick(5f)

    assertFalse(world.has(id, Casting::class))
    verify(exactly = 0) { skillExecutionService.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a fresh cast is dirty so the client is told the bar opened`() {
    val id = castingEntity(castTime = 3f)

    assertTrue(world.get(id, Casting::class)!!.isDirty())
  }

  @Test
  fun `ticking below the sync interval does not re-dirty the component`() {
    val id = castingEntity(castTime = 5f)
    val casting = world.get(id, Casting::class)!!
    casting.clearDirty()

    repeat(10) { world.tick(0.05f) }

    assertEquals(4.5f, casting.remainingSeconds, TOLERANCE, "the countdown still runs every tick")
    assertFalse(casting.isDirty(), "half a second of ticks must not produce half a second of packets")
  }

  @Test
  fun `crossing the sync interval re-dirties so the client bar stays corrected`() {
    val id = castingEntity(castTime = 5f)
    val casting = world.get(id, Casting::class)!!
    casting.clearDirty()

    repeat(20) { world.tick(0.05f) }

    assertTrue(casting.isDirty())
  }

  @Test
  fun `the tick a cast elapses on does not dirty - its removal ends the bar`() {
    val id = castingEntity(castTime = 1f)
    val casting = world.get(id, Casting::class)!!
    casting.clearDirty()

    world.tick(1f)

    assertFalse(world.has(id, Casting::class))
    // A heartbeat carrying remainingSeconds = 0 would make the client hide the bar a beat before
    // the removal arrives, so the elapsing tick deliberately stays clean.
    assertFalse(casting.isDirty())
  }

  @Test
  fun `ground targeted cast carries the position through to execution`() {
    val position = Vec3L(4, 0, 7)
    val id = world.createEntity { entityId ->
      add(
        entityId, Casting(
          skillId = SKILL_ID,
          skillLevel = 1,
          targetEntityId = null,
          targetPosition = position,
          totalSeconds = 1f
        )
      )
    }

    world.tick(1f)

    verify(exactly = 1) {
      skillExecutionService.execute(
        world = world,
        casterId = id,
        skillId = SKILL_ID,
        skillLevel = 1,
        targetEntityId = null,
        targetPosition = position
      )
    }
  }

  companion object {
    private const val SKILL_ID = 5L
    private const val TOLERANCE = 0.0001f
  }
}
