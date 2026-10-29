package net.bestia.zone.teleport

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.SavePointService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A wing is only spent when the body actually sets off, so every refusal has to return false. */
class WingScriptTest {

  private val settings = TeleportItemSettings()

  private lateinit var world: World
  private lateinit var out: OutMessageProcessor
  private lateinit var statusEffects: StatusEffectService

  @BeforeEach
  fun setUp() {
    world = testWorld()
    out = mockk(relaxed = true)
    statusEffects = mockk(relaxed = true)
  }

  private fun flyWing() = FlyWingScript(statusEffects, out, settings)

  private fun user(): EntityId = world.createEntity { id ->
    add(id, Position(100, 100, 70))
    add(id, Account(ACCOUNT_ID))
    add(id, Master(MASTER_ID))
    add(id, Path(mutableListOf(Vec3L(101, 100, 70))))
  }

  @Test
  fun `a fly wing starts the vanish and locks the next one`() {
    val id = user()

    assertTrue(flyWing().execute(world, id, ScriptArgs.EMPTY))

    val teleporting = world.get(id, Teleporting::class)
    assertEquals(Teleporting.Destination.Nearby(150), teleporting?.destination)
    assertEquals(0.25f, teleporting?.secondsUntilMove)
    assertEquals(EntityFx.Kind.WING_VANISH, world.get(id, EntityFx::class)?.kind)
    assertFalse(world.has(id, Path::class), "the body stands still while it vanishes")
    verify { statusEffects.applyEffect(world, id, StatusEffectId.TELEPORT_LOCK.id, 1, id) }
  }

  @Test
  fun `a wing under the lock of the last one is not spent`() {
    val id = user()
    world.add(id, StatusEffects().apply {
      applyEffect(StatusEffectId.TELEPORT_LOCK.id, StackBehavior.IGNORE_IF_PRESENT, 1, id, 3.5, false)
    })

    assertFalse(flyWing().execute(world, id, ScriptArgs.EMPTY))

    assertFalse(world.has(id, Teleporting::class))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TELEPORT_NOT_READY)) }
  }

  @Test
  fun `a wing is refused mid-cast`() {
    val id = user()
    world.add(id, Casting(skillId = 1, skillLevel = 1, targetEntityId = id, targetPosition = null, totalSeconds = 2f))

    assertFalse(flyWing().execute(world, id, ScriptArgs.EMPTY))

    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TELEPORT_NOT_NOW)) }
  }

  @Test
  fun `a second wing while the first still plays is refused`() {
    val id = user()
    flyWing().execute(world, id, ScriptArgs.EMPTY)

    assertFalse(flyWing().execute(world, id, ScriptArgs.EMPTY))
  }

  @Test
  fun `a butterfly wing heads for the master's save point`() {
    val id = user()
    val savePoints = mockk<SavePointService>()
    every { savePoints.forMaster(MASTER_ID) } returns Vec3L(500, 600, 10)
    val asyncJobs = mockk<AsyncJobExecutor>()
    val job = slot<() -> Unit>()
    every { asyncJobs.submit(any(), capture(job)) } answers { job.captured() }
    val worldView = mockk<WorldView>()
    val task = slot<World.() -> Unit>()
    every { worldView.post(capture(task)) } answers { world.(task.captured)() }

    val script = ButterflyWingScript(statusEffects, out, settings, savePoints, asyncJobs, worldView)
    assertTrue(script.execute(world, id, ScriptArgs.EMPTY))

    val home = assertIs<Teleporting.Destination.Home>(world.get(id, Teleporting::class)?.destination)
    assertEquals(Vec3L(500, 600, 10), home.savePoint)
    assertEquals(EntityFx.Kind.HOME_VANISH, world.get(id, EntityFx::class)?.kind)
  }

  private companion object {
    const val ACCOUNT_ID = 7L
    const val MASTER_ID = 3L
  }
}
