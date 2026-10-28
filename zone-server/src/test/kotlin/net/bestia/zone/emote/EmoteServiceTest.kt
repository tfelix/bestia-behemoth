package net.bestia.zone.emote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmoteServiceTest {

  private val world = testWorld()
  private val gate = mockk<BasicSkillGate> { every { mayEmote(ACCOUNT_ID) } returns true }
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val entity = world.createEntity { id -> add(id, Animation()) }

  private val sut = EmoteService(
    connectionInfoService = ConnectionInfoService().apply {
      activateSession(ACCOUNT_ID, masterId = 1L, masterEntityId = entity)
    },
    basicSkillGate = gate,
    deadActionGuard = DeadActionGuard(),
    outMessageProcessor = messages,
  )

  private fun pose() = world.getOrThrow(entity, Animation::class).currentAnimation

  @Test
  fun `an emote becomes the entity's pose`() {
    sut.perform(world, ACCOUNT_ID, AnimationKind.SIT)

    assertEquals(AnimationKind.SIT, pose())
  }

  @Test
  fun `below Basic Skill rank 2 the player is told why`() {
    every { gate.mayEmote(ACCOUNT_ID) } returns false

    sut.perform(world, ACCOUNT_ID, AnimationKind.CHEER)

    assertEquals(AnimationKind.IDLE, pose())
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.BASIC_SKILL_EMOTE_LOCKED)) }
  }

  @Test
  fun `the AI's poses cannot be asked for`() {
    sut.perform(world, ACCOUNT_ID, AnimationKind.SLEEP)

    assertEquals(AnimationKind.IDLE, pose())
  }

  @Test
  fun `walking or casting refuses it with a reason`() {
    world.add(entity, Path(mutableListOf(Vec3L(1, 0, 0))))
    sut.perform(world, ACCOUNT_ID, AnimationKind.SIT)
    world.remove(entity, Path::class)

    world.add(entity, Casting(skillId = 1L, skillLevel = 1, targetEntityId = 0L, targetPosition = null, totalSeconds = 1f))
    sut.perform(world, ACCOUNT_ID, AnimationKind.SIT)

    assertEquals(AnimationKind.IDLE, pose())
    verify(exactly = 2) { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.EMOTE_NOT_NOW)) }
  }

  @Test
  fun `a dead body does not emote`() {
    world.add(entity, Dead())

    sut.perform(world, ACCOUNT_ID, AnimationKind.CHEER)

    assertEquals(AnimationKind.IDLE, pose())
  }

  /** The spam guard, and what makes a second cheer replay: it has to go back to IDLE first. */
  @Test
  fun `a once emote plays out before another starts`() {
    sut.perform(world, ACCOUNT_ID, AnimationKind.CHEER)
    sut.perform(world, ACCOUNT_ID, AnimationKind.JUMP)

    assertEquals(AnimationKind.CHEER, pose())
  }

  @Test
  fun `a lasting emote can be swapped for another`() {
    sut.perform(world, ACCOUNT_ID, AnimationKind.SIT)
    sut.perform(world, ACCOUNT_ID, AnimationKind.LIE)

    assertEquals(AnimationKind.LIE, pose())
  }

  private companion object {
    const val ACCOUNT_ID = 7L
  }
}
