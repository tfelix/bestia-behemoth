package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.persistence.DivergenceState
import net.bestia.zone.prop.persistence.WorldObjectDivergenceRepository
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** How a removed tree grows back over the weeks, against a clock the test moves. */
class TreeRegrowthSystemTest {

  private class SteppedClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
  }

  private val felledAt = Instant.parse("2026-11-06T12:00:00Z")
  private val week = Duration.ofDays(7)
  private val clock = SteppedClock(felledAt)

  private val worldService: WorldService = mockk {
    every { record } returns mockk {
      every { pipelineVersion } returns 1L
      every { shapeVersion } returns 1L
    }
  }

  // The real registry, writing inline, so what one sweep records is what the next one reads.
  private val divergence = WorldObjectDivergenceRegistry(
    mockk<WorldObjectDivergenceRepository>(relaxed = true) { every { save(any()) } answers { firstArg() } },
    mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    worldService
  )

  private var seedTreeNearby = true
  private val neighbourhood: TreeNeighbourhood = mockk {
    every { columnOf(any()) } returns ChunkPos(0, 0, 0)
    every { hasGrownTreeNear(any(), 12L) } answers { seedTreeNearby }
  }

  private val residency: WorldObjectResidencyService = mockk(relaxed = true) {
    every { entitiesIn(any(), any()) } returns LongArray(0)
  }

  private val system =
    TreeRegrowthSystem(divergence, PropKindRegistry().also { it.load() }, neighbourhood, residency, clock)

  private fun felled(propId: Long) {
    divergence.record(propId, StaticEntityKind.TREE, DivergenceState.STUMP, felledAt.plus(week))
  }

  private fun sweepAt(at: Instant) {
    clock.now = at
    system.update(testWorld(), 60f)
  }

  @Test
  fun `a stump next to a grown tree sprouts a week after it was felled`() {
    felled(TREE)

    sweepAt(felledAt.plus(week).minusSeconds(1))
    assertEquals(DivergenceState.STUMP, divergence.of(TREE)?.state, "a day early, nothing grows")

    sweepAt(felledAt.plus(week))
    assertEquals(DivergenceState.SAPLING, divergence.of(TREE)?.state)
    assertEquals(felledAt.plus(week.multipliedBy(2)), divergence.of(TREE)?.resumeAt)
  }

  @Test
  fun `a stump with no grown tree near stays a stump and tries again a week later`() {
    seedTreeNearby = false
    felled(TREE)

    sweepAt(felledAt.plus(week))

    assertEquals(DivergenceState.STUMP, divergence.of(TREE)?.state)
    assertEquals(felledAt.plus(week.multipliedBy(2)), divergence.of(TREE)?.resumeAt)
  }

  @Test
  fun `a sapling keeps growing even after its wood is felled`() {
    felled(TREE)
    sweepAt(felledAt.plus(week))
    seedTreeNearby = false

    sweepAt(felledAt.plus(week.multipliedBy(2)))
    assertEquals(DivergenceState.YOUNG, divergence.of(TREE)?.state)

    sweepAt(felledAt.plus(week.multipliedBy(3)))
    assertNull(divergence.of(TREE), "a grown tree has no row, and stands as the generator made it")
  }

  @Test
  fun `after a month of downtime a tree catches up one stage per sweep`() {
    felled(TREE)
    val after = felledAt.plus(week.multipliedBy(5))

    sweepAt(after)
    assertEquals(DivergenceState.SAPLING, divergence.of(TREE)?.state)
    sweepAt(after)
    assertEquals(DivergenceState.YOUNG, divergence.of(TREE)?.state)
    sweepAt(after)
    assertNull(divergence.of(TREE))
  }

  @Test
  fun `a held tree is swapped for its next stage`() {
    felled(TREE)
    val world = testWorld()
    val stump = world.createEntity { id -> add(id, WorldObjectIdentity(TREE, latticeVersion = 1L)) }
    every { residency.entitiesIn(0, 0) } returns longArrayOf(stump)

    clock.now = felledAt.plus(week)
    system.update(world, 60f)

    verify { residency.replace(world, stump, GrowthStage.SAPLING) }
  }

  @Test
  fun `a tree in a fight grows at a later sweep`() {
    divergence.record(TREE, StaticEntityKind.TREE, DivergenceState.SAPLING, felledAt.plus(week))
    val world = testWorld()
    val sapling = world.createEntity { id ->
      add(id, WorldObjectIdentity(TREE, latticeVersion = 1L))
      add(id, Position.fromVec3(Vec3L(1, 2, 3)))
    }
    every { residency.entitiesIn(0, 0) } returns longArrayOf(sapling)

    clock.now = felledAt.plus(week)
    system.update(world, 60f)

    assertEquals(DivergenceState.SAPLING, divergence.of(TREE)?.state)
    verify(exactly = 0) { residency.replace(any(), any(), any()) }
  }

  @Test
  fun `one sweep grows a bounded number of trees`() {
    repeat(100) { felled(TREE + it) }

    sweepAt(felledAt.plus(week))

    val sprouted = (0 until 100).count { divergence.of(TREE + it)?.state == DivergenceState.SAPLING }
    assertEquals(64, sprouted)
  }

  private companion object {
    const val TREE = 1000L
  }
}
