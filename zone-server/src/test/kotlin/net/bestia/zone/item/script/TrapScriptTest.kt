package net.bestia.zone.item.script

import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.capture.BestiaTrap
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A trap is only consumed when it was actually set, so every refusal has to return false. */
class TrapScriptTest {

  private val standingAt = Vec3L(100, 100, 64)

  private lateinit var world: World
  private lateinit var out: OutMessageProcessor
  private lateinit var script: BestiaTrapScript

  @BeforeEach
  fun setUp() {
    world = testWorld()
    out = mockk(relaxed = true)
    script = BestiaTrapScript(out)
  }

  @Test
  fun `a trap aimed within reach is set on that tile`() {
    val user = spawnMaster()
    val at = Vec3L(standingAt.x + 10, standingAt.y, standingAt.z)

    assertTrue(script.execute(world, user, argsAt(at)))

    val traps = trapPositions()
    assertEquals(listOf(at), traps)
  }

  @Test
  fun `a trap aimed out of reach is not spent`() {
    val user = spawnMaster()
    val tooFar = Vec3L(standingAt.x + 11, standingAt.y, standingAt.z)

    assertFalse(script.execute(world, user, argsAt(tooFar)))

    assertTrue(trapPositions().isEmpty())
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TRAP_OUT_OF_RANGE)) }
  }

  @Test
  fun `a second trap on the same tile is refused`() {
    val user = spawnMaster()
    script.execute(world, user, argsAt(standingAt))

    assertFalse(script.execute(world, user, argsAt(standingAt)))

    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TRAP_NO_ROOM)) }
  }

  @Test
  fun `a master may only have so many traps out`() {
    val user = spawnMaster()
    repeat(TrapScript.MAX_TRAPS) { i ->
      assertTrue(script.execute(world, user, argsAt(Vec3L(standingAt.x + i, standingAt.y, standingAt.z))))
    }

    assertFalse(script.execute(world, user, argsAt(Vec3L(standingAt.x, standingAt.y + 1, standingAt.z))))

    verify {
      out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TRAP_LIMIT_REACHED, listOf("${TrapScript.MAX_TRAPS}")))
    }
  }

  @Test
  fun `a bestia cannot set a trap`() {
    val bestia = world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Account(ACCOUNT_ID))
    }

    assertFalse(script.execute(world, bestia, argsAt(standingAt)))
  }

  @Test
  fun `a trap used with no position is not spent`() {
    assertFalse(script.execute(world, spawnMaster(), ScriptArgs.EMPTY))
  }

  private fun trapPositions(): List<Vec3L> {
    val found = mutableListOf<Vec3L>()
    world.query(BestiaTrap::class, Position::class).each { found.add(get<Position>().toVec3L()) }

    return found
  }

  private fun spawnMaster(): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Master(MASTER_ID))
      add(id, Account(ACCOUNT_ID))
    }
  }

  private fun argsAt(position: Vec3L): ScriptArgs {
    return ScriptArgs.of(ScriptArgKeys.POSITION to position)
  }

  private companion object {
    const val MASTER_ID = 7L
    const val ACCOUNT_ID = 3L
  }
}
