package net.bestia.zone.ecs.spawn

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ticks are paced at a realistic `dt`, never in one coarse step.
 *
 * `Schedule.EverySeconds` accumulates the tick delta and subtracts one period when it fires, which is only
 * equivalent to "elapsed since last run" while a single tick is shorter than the period. A test that ticked
 * at 1 s against a 0.25 s schedule would drive the system's own clock several times faster than simulated
 * time and would be asserting the scheduler's arithmetic rather than the unload delay.
 */
class SpawnerSystemTest {

  private val bestiaSpawner: BestiaEntitySpawner = mockk()
  private val cellIndex = SpawnerCellIndex()
  private val world = testWorld(systems = listOf(SpawnerSystem(bestiaSpawner, cellIndex)))

  /** Every mob the mocked spawner has produced, in order, so a test can tell a survivor from a replacement. */
  private val spawned = mutableListOf<EntityId>()

  init {
    every { bestiaSpawner.spawnMob(any(), any<Long>(), any(), any()) } answers {
      world.createEntity { }.also { spawned.add(it) }
    }
  }

  private val tickRate = 20
  private val dt = 1f / tickRate

  private fun tick(seconds: Float) = repeat((seconds * tickRate).toInt()) { world.tick(dt) }

  private fun placeDen(
    position: Vec3L,
    pack: Int = 3,
    activationRange: Int = 100,
  ): Spawner {
    val spawner = Spawner(
      bestiaId = 7L,
      maxSpawnCount = pack,
      position = position,
      range = 10,
      activationRange = activationRange,
    )
    val id = world.createEntity { it -> world.add(it, spawner) }
    cellIndex.add(id, position)
    return spawner
  }

  private fun placePlayer(position: Vec3L): Position {
    val pos = Position.fromVec3(position)
    world.createEntity { id ->
      world.add(id, pos)
      world.add(id, ActivePlayer)
    }
    return pos
  }

  private fun moveTo(player: Position, position: Vec3L) {
    player.x = position.x
    player.y = position.y
    player.z = position.z
  }

  private fun livingPackOf(den: Spawner) = den.spawnedEntities.filter { world.hasEntity(it) }

  @Test
  fun `a den with no player near it never spawns anything`() {
    val den = placeDen(Vec3L(100_000, 100_000, 0))
    placePlayer(Vec3L(0, 0, 0))

    tick(5f)

    assertTrue(den.spawnedEntities.isEmpty())
    verify(exactly = 0) { bestiaSpawner.spawnMob(any(), any<Long>(), any(), any()) }
  }

  @Test
  fun `a player inside the activation range stocks the den to its pack size`() {
    val den = placeDen(Vec3L(0, 0, 0), pack = 3)
    placePlayer(Vec3L(50, 0, 0))

    tick(5f)

    assertEquals(3, den.spawnedEntities.size)
    assertEquals(3, livingPackOf(den).size)
  }

  @Test
  fun `a den far below a player is still activated`() {
    // Activation is horizontal on purpose: a den in a gallery under a player's feet is well inside the range
    // that matters. This is what a cube-shaped broad phase would have got wrong, and why the cell key has no
    // z axis at all.
    val den = placeDen(Vec3L(0, 0, -300), pack = 1)
    placePlayer(Vec3L(20, 0, 0))

    tick(2f)

    assertEquals(1, den.spawnedEntities.size)
  }

  @Test
  fun `the pack survives a brief absence and is not rebuilt when the player returns`() {
    val den = placeDen(Vec3L(0, 0, 0), pack = 2)
    val player = placePlayer(Vec3L(50, 0, 0))
    tick(5f)
    val original = den.spawnedEntities.toList()
    assertEquals(2, original.size)

    moveTo(player, Vec3L(50_000, 0, 0))
    tick(SpawnerSystem.UNLOAD_DELAY_SECONDS / 2)

    assertEquals(original.toSet(), livingPackOf(den).toSet(), "the pack was torn down inside the unload delay")

    moveTo(player, Vec3L(50, 0, 0))
    tick(5f)

    // The same creatures, not replacements: this is the whole reason the delay exists. Stepping out of a camp
    // and back must not reset the creatures in it - including their damage, their aggro and where they stand.
    assertEquals(original.toSet(), livingPackOf(den).toSet())
    verify(exactly = 2) { bestiaSpawner.spawnMob(any(), any<Long>(), any(), any()) }
  }

  @Test
  fun `the pack is despawned once the den has been idle for the whole delay, and is not restocked`() {
    val den = placeDen(Vec3L(0, 0, 0), pack = 2)
    val player = placePlayer(Vec3L(50, 0, 0))
    tick(5f)
    val original = den.spawnedEntities.toList()

    moveTo(player, Vec3L(50_000, 0, 0))
    tick(SpawnerSystem.UNLOAD_DELAY_SECONDS + 5f)

    assertTrue(den.spawnedEntities.isEmpty(), "spawnedEntities must be cleared so the den restocks fresh")
    original.forEach { assertFalse(world.hasEntity(it), "entity $it survived the despawn") }

    // Long past expiry the den must stay quiet. A den that stayed in `stocked` would keep being handed to
    // spawnMissingEntities, and one whose idle stamp was refreshed every pass would never have expired at all.
    tick(SpawnerSystem.UNLOAD_DELAY_SECONDS * 2)

    assertTrue(den.spawnedEntities.isEmpty())
    verify(exactly = 2) { bestiaSpawner.spawnMob(any(), any<Long>(), any(), any()) }
  }

  @Test
  fun `a den in range of two players stays stocked when only one of them leaves`() {
    val den = placeDen(Vec3L(0, 0, 0), pack = 2)
    val leaving = placePlayer(Vec3L(50, 0, 0))
    placePlayer(Vec3L(0, 50, 0))
    tick(5f)
    val original = den.spawnedEntities.toList()
    assertEquals(2, original.size)

    moveTo(leaving, Vec3L(50_000, 0, 0))
    tick(SpawnerSystem.UNLOAD_DELAY_SECONDS + 5f)

    assertEquals(original.toSet(), livingPackOf(den).toSet(), "the den went quiet while a player was still in it")
  }

  @Test
  fun `a dead pack member is replaced while the den is stocked`() {
    val den = placeDen(Vec3L(0, 0, 0), pack = 2)
    placePlayer(Vec3L(50, 0, 0))
    tick(5f)
    val casualty = den.spawnedEntities.first()

    world.destroy(casualty)
    tick(5f)

    assertEquals(2, livingPackOf(den).size)
    assertFalse(casualty in den.spawnedEntities, "the dead member was never pruned from the den's set")
  }
}
