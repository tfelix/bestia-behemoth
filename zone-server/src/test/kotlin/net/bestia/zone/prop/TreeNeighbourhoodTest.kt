package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.persistence.DivergenceState
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Whether a removed tree has a grown one near enough to sprout from.
 *
 * Against hand-placed trees on the real 4 m lattice and 32 m columns, so the distances are the ones a player
 * would pace out.
 */
class TreeNeighbourhoodTest {

  private val trees = HashMap<ChunkPos, MutableList<WorldObjectSite>>()
  private val rows = HashMap<Long, DivergenceEntry>()

  private val worldService: WorldService = mockk {
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { generated.params } returns WorldParams.DEFAULT
  }

  private val generated: GeneratedPropSource = mockk {
    every { sitesIn(any()) } answers { trees[firstArg<ChunkPos>()].orEmpty() }
  }

  private val divergence: WorldObjectDivergenceRegistry = mockk {
    every { of(any()) } answers { rows[firstArg<Long>()] }
  }

  private val neighbourhood =
    TreeNeighbourhood(worldService, generated, PropKindRegistry().also { it.load() }, divergence)

  /** A tree in lattice cell ([cellX], [cellY]), standing at the cell's centre. */
  private fun tree(cellX: Long, cellY: Long, kind: StaticEntityKind = StaticEntityKind.TREE): Long {
    val propId = PropId.of(PropKind.TREE, cellX, cellY)
    val position = Vec3L(cellX * 4 + 2, cellY * 4 + 2, 64)
    val column = ChunkPos(Math.floorDiv(position.x, 32L).toInt(), Math.floorDiv(position.y, 32L).toInt(), 0)

    trees.getOrPut(column) { ArrayList() }
      .add(WorldObjectSite(kind, propId, position, variant = 0, heightDm = 80, yaw = 0f))
    return propId
  }

  @Test
  fun `a tree knows the column it stands in`() {
    assertEquals(ChunkPos(0, 0, 0), neighbourhood.columnOf(tree(2, 2)))
    assertEquals(ChunkPos(1, 0, 0), neighbourhood.columnOf(tree(8, 7)))
    assertEquals(ChunkPos(-1, 0, 0), neighbourhood.columnOf(tree(-1, 0)))
  }

  @Test
  fun `a grown tree 12 m away is near enough, one 16 m away is not`() {
    val stump = tree(2, 2)
    tree(5, 2)

    assertTrue(neighbourhood.hasGrownTreeNear(stump, radius = 12))
    assertFalse(neighbourhood.hasGrownTreeNear(stump, radius = 11))

    val lonely = tree(2, 6)
    rows[stump] = DivergenceEntry(StaticEntityKind.TREE, DivergenceState.STUMP, Instant.EPOCH)
    assertFalse(neighbourhood.hasGrownTreeNear(lonely, radius = 12), "its only neighbour is a stump, 16 m off")
  }

  @Test
  fun `a stump, a sapling or a young tree is no seed tree`() {
    val stump = tree(2, 2)
    val sapling = tree(3, 2)
    rows[sapling] = DivergenceEntry(StaticEntityKind.TREE, DivergenceState.SAPLING, Instant.EPOCH)
    val young = tree(2, 3)
    rows[young] = DivergenceEntry(StaticEntityKind.TREE, DivergenceState.YOUNG, Instant.EPOCH)

    assertFalse(neighbourhood.hasGrownTreeNear(stump, radius = 12))
  }

  @Test
  fun `a blighted tree seeds as well as a healthy one`() {
    val stump = tree(2, 2)
    tree(3, 3, StaticEntityKind.BLIGHTED_TREE)

    assertTrue(neighbourhood.hasGrownTreeNear(stump, radius = 12))
  }

  @Test
  fun `a grown tree across a column edge counts`() {
    val stump = tree(7, 2) // x = 30, the last cell of column 0
    tree(9, 2) // x = 38, in column 1

    assertTrue(neighbourhood.hasGrownTreeNear(stump, radius = 12))
    verify { generated.sitesIn(ChunkPos(1, 0, 0)) }
  }

  @Test
  fun `a column is read from the generator once`() {
    val stump = tree(2, 2)
    tree(5, 2)

    repeat(3) { neighbourhood.hasGrownTreeNear(stump, radius = 12) }

    verify(exactly = 1) { generated.sitesIn(ChunkPos(0, 0, 0)) }
  }
}
