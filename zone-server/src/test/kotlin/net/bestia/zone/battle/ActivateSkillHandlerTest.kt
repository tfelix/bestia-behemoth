package net.bestia.zone.battle

import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillCheckService
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.ecs.logout.LogoutIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

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

  private fun handlerFor(caster: EntityId, skill: Skill): ActivateSkillHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(ACCOUNT_ID, masterId = 1L, masterEntityId = caster)

    val repository = mockk<SkillRepository>()
    every { repository.findById(skill.id) } returns Optional.of(skill)

    return ActivateSkillHandler(
      connectionInfoService = connectionInfoService,
      skillCheckService = SkillCheckService(world),
      world = world,
      skillRepository = repository,
      skillStrategyFactory = SkillStrategyFactory(listOf(TestScript())),
      skillExecutionService = skillExecution,
      logoutCancelService = LogoutCancelService(world),
      propPromotion = PropPromotionService(mockk(relaxed = true)),
      outMessageProcessor = mockk(relaxed = true),
    )
  }

  @Test
  fun `an instant skill resolves at once and puts up no cast bar`() {
    val caster = world.spawnCaster()
    handlerFor(caster, skill(castTime = 0f)).handle(activate(caster))

    assertFalse(world.has(caster, Casting::class), "a skill with no cast time must not attach a cast bar")
    verify(exactly = 1) {
      skillExecution.execute(any(), caster, SKILL_ID, 1, caster, null)
    }
  }

  @Test
  fun `a channelled skill puts up a cast bar and resolves nothing yet`() {
    val caster = world.spawnCaster()
    handlerFor(caster, skill(castTime = 2f)).handle(activate(caster))

    assertTrue(world.has(caster, Casting::class), "a skill with a cast time is resolved by CastingSystem later")
    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `activating a skill aborts a pending logout`() {
    val caster = world.spawnCaster()
    world.add(caster, LogoutIntent())

    handlerFor(caster, skill(castTime = 0f)).handle(activate(caster))

    assertFalse(world.has(caster, LogoutIntent::class), "casting is player activity and cancels a logout")
  }

  @Test
  fun `a skill the caster has not learned is refused`() {
    val caster = world.spawnCaster(knownLevel = 0)
    handlerFor(caster, skill(castTime = 0f)).handle(activate(caster))

    assertFalse(world.has(caster, Casting::class))
    verify(exactly = 0) { skillExecution.execute(any(), any(), any(), any(), any(), any()) }
  }

  private fun activate(target: EntityId) = ActivateSkillCMSG(
    playerId = ACCOUNT_ID,
    attackId = SKILL_ID,
    skillLevel = 1,
    targetPosition = Vec3L.ZERO,
    targetEntityId = target
  )

  /** [script] names `TestScript`, the simple name a `SkillStrategyFactory` keys the test script under. */
  private fun skill(castTime: Float) = Skill(
    id = SKILL_ID,
    identifier = "TEST_SKILL",
    strength = null,
    script = "TestScript",
    manaCost = 0,
    range = 100,
    targetType = SkillTargetType.ENEMY,
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
  }
}
