package net.bestia.zone.casting.net

import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.ownByPlayer
import net.bestia.zone.battle.damage.wardPlayer
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.logout.ecs.LogoutIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import net.bestia.zone.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.casting.SkillCheckService
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.casting.SkillStrategyFactory

/**
 * The cast-time branch, which is the whole job of this handler: an instant skill is handed straight to
 * [SkillExecutionService], a channelled one waits behind a [Casting] component for `CastingSystem`.
 *
 * Worth pinning because getting it wrong is silent from here and loud two layers down - `Casting` refuses a
 * non-positive `totalSeconds`, so putting every skill through the channelled branch throws inside the
 * handler, and `InMessageProcessor` turns a handler exception into a dropped connection.
 */
class ActivateSkillHandlerTest {

  /** Castable, and does nothing: what the handler does with a strategy is all these tests are about. */
  private class TestScript : SkillStrategy {
    override fun isCastPossible(ctx: SkillContext) = true
    override fun execute(ctx: SkillContext): Damage? = null
  }

  private val world = testWorld()
  private val skillExecution = mockk<SkillExecutionService>(relaxed = true)
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private fun handlerFor(caster: EntityId, skill: Skill): ActivateSkillHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(ACCOUNT_ID, masterId = 1L, masterEntityId = caster)

    every { skillExecution.skillOf(skill.id) } returns skill

    return ActivateSkillHandler(
      connectionInfoService = connectionInfoService,
      skillCheckService = SkillCheckService(world),
      skillStrategyFactory = SkillStrategyFactory(listOf(TestScript())),
      skillExecutionService = skillExecution,
      logoutCancelService = LogoutCancelService(),
      deadActionGuard = DeadActionGuard(),
      propPromotion = PropPromotionService(mockk(relaxed = true)),
      outMessageProcessor = messages,
    )
  }

  @Test
  fun `an instant skill resolves at once and puts up no cast bar`() {
    val caster = world.spawnCaster()
    handlerFor(caster, skill(castTime = 0f)).handle(world, activate(caster))

    assertFalse(world.has(caster, Casting::class), "a skill with no cast time must not attach a cast bar")
    verify(exactly = 1) {
      skillExecution.execute(any(), caster, SKILL_ID, 1, caster, null)
    }
  }

  @Test
  fun `a channelled skill puts up a cast bar and resolves nothing yet`() {
    val caster = world.spawnCaster()
    handlerFor(caster, skill(castTime = 2f)).handle(world, activate(caster))

    assertTrue(world.has(caster, Casting::class), "a skill with a cast time is resolved by CastingSystem later")
    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `activating a skill aborts a pending logout`() {
    val caster = world.spawnCaster()
    world.add(caster, LogoutIntent())

    handlerFor(caster, skill(castTime = 0f)).handle(world, activate(caster))

    assertFalse(world.has(caster, LogoutIntent::class), "casting is player activity and cancels a logout")
  }

  @Test
  fun `a skill the caster has not learned is refused`() {
    val caster = world.spawnCaster(knownLevel = 0)
    handlerFor(caster, skill(castTime = 0f)).handle(world, activate(caster))

    assertFalse(world.has(caster, Casting::class))
    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  /** An unlearned skill counts as level 0, so asking for level 0 used to pass the "known at this level" check. */
  @Test
  fun `an unlearned skill cast at level zero is refused`() {
    val caster = world.spawnCaster(knownLevel = 0)

    handlerFor(caster, skill(castTime = 0f)).handle(world, activate(caster, skillLevel = 0))

    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a negative skill level is refused`() {
    val caster = world.spawnCaster(knownLevel = 1)

    handlerFor(caster, skill(castTime = 0f)).handle(world, activate(caster, skillLevel = -1))

    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a ground skill is cast at the position even when an entity was sent`() {
    val caster = world.spawnCaster()

    handlerFor(caster, skill(castTime = 0f, targetType = SkillTargetType.GROUND)).handle(world, activate(caster))

    verify(exactly = 1) { skillExecution.execute(any(), caster, SKILL_ID, 1, null, Vec3L.ZERO) }
  }

  @Test
  fun `an enemy skill without a target is refused`() {
    val caster = world.spawnCaster()

    handlerFor(caster, skill(castTime = 0f, targetType = SkillTargetType.ENEMY)).handle(world, activate(NO_TARGET))

    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `an enemy skill aimed at a warded player is refused with the ward`() {
    val caster = world.spawnCaster().also { world.ownByPlayer(it) }
    val warded = world.spawnCaster().also { world.wardPlayer(it) }

    handlerFor(caster, skill(castTime = 0f, targetType = SkillTargetType.ENEMY)).handle(world, activate(warded))

    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.COMBAT_TARGET_WARDED)) }
  }

  /** The Skills window's "Use" button sends no target, and a friendly skill used that way means the caster. */
  @Test
  fun `a friendly skill without a target is cast on the caster`() {
    val caster = world.spawnCaster()

    handlerFor(caster, skill(castTime = 0f, targetType = SkillTargetType.FRIENDLY)).handle(world, activate(NO_TARGET))

    verify(exactly = 1) { skillExecution.execute(any(), caster, SKILL_ID, 1, caster, null) }
  }

  private fun activate(target: EntityId, skillLevel: Int = 1) = ActivateSkillCMSG(
    playerId = ACCOUNT_ID,
    attackId = SKILL_ID,
    skillLevel = skillLevel,
    targetPosition = Vec3L.ZERO,
    targetEntityId = target
  )

  /** [script] names `TestScript`, the simple name a `SkillStrategyFactory` keys the test script under. */
  private fun skill(castTime: Float, targetType: SkillTargetType = SkillTargetType.ENEMY) = Skill(
    id = SKILL_ID,
    identifier = "TEST_SKILL",
    strength = null,
    script = "TestScript",
    manaCost = 0,
    range = 100,
    targetType = targetType,
    needsLineOfSight = false,
    castTime = castTime,
    requiredLevel = 0
  )

  private fun World.spawnCaster(knownLevel: Int = 1): EntityId = createEntity { id ->
    add(id, Position(0, 0, 0))
    add(id, KnownSkills(mutableMapOf(SKILL_ID to knownLevel)))
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val SKILL_ID = 1L
    const val NO_TARGET = 0L
  }
}
