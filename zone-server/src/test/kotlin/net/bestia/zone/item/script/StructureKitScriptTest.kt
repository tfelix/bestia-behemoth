package net.bestia.zone.item.script

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.StaticEntityKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A kit is only consumed when it actually became a site, so every refusal here has to return false - see the
 * class note on [StructureKitScript].
 */
class StructureKitScriptTest {

  private val standingAt = Vec3L(100, 100, 64)

  private lateinit var world: World
  private lateinit var structures: PlayerStructureService
  private lateinit var script: StructureKitScript

  @BeforeEach
  fun setUp() {
    world = testWorld()
    structures = mockk()
    every { structures.beginConstruction(any(), any(), any(), any(), any(), any()) } returns 1L

    script = object : StructureKitScript(structures, StaticEntityKind.WORKBENCH, BUILD_SECONDS) {
      override val itemId = 26L
    }
  }

  @Test
  fun `a kit aimed at reachable ground puts up a site`() {
    val user = spawnMaster()

    val used = script.execute(world, user, argsAt(standingAt, yaw = 1.5))

    assertTrue(used, "the kit is spent")
    verify {
      structures.beginConstruction(world, StaticEntityKind.WORKBENCH, MASTER_ID, standingAt, 1.5f, BUILD_SECONDS)
    }
  }

  @Test
  fun `no yaw means facing north`() {
    val user = spawnMaster()

    script.execute(world, user, ScriptArgs.of(ScriptArgKeys.POSITION to standingAt))

    verify { structures.beginConstruction(any(), any(), any(), any(), 0f, any()) }
  }

  @Test
  fun `a kit used with no position is not spent`() {
    val user = spawnMaster()

    assertFalse(script.execute(world, user, ScriptArgs.EMPTY))

    verify(exactly = 0) { structures.beginConstruction(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a kit aimed out of reach is not spent`() {
    val user = spawnMaster()

    val tooFar = Vec3L(standingAt.x + 20, standingAt.y, standingAt.z)

    assertFalse(script.execute(world, user, argsAt(tooFar)))

    verify(exactly = 0) { structures.beginConstruction(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a bestia carrying a kit cannot build with it`() {
    val bestia = world.createEntity { id -> add(id, Position.fromVec3(standingAt)) }

    assertFalse(script.execute(world, bestia, argsAt(standingAt)))

    verify(exactly = 0) { structures.beginConstruction(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `ground that is already taken leaves the kit in the bag`() {
    val user = spawnMaster()
    every { structures.beginConstruction(any(), any(), any(), any(), any(), any()) } returns null

    assertFalse(script.execute(world, user, argsAt(standingAt)))
  }

  private fun spawnMaster(): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Master(MASTER_ID))
    }
  }

  private fun argsAt(position: Vec3L, yaw: Double? = null): ScriptArgs {
    if (yaw == null) {
      return ScriptArgs.of(ScriptArgKeys.POSITION to position)
    }

    return ScriptArgs.of(ScriptArgKeys.POSITION to position, ScriptArgKeys.YAW to yaw)
  }

  private companion object {
    const val MASTER_ID = 7L
    const val BUILD_SECONDS = 20f
  }
}
