package net.bestia.zone.doomsday.withering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.voxel.PropInstances
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.doomsday.persistence.StoredWitheredCellRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import org.junit.jupiter.api.Test

class WitheringBlightTest {

  private val field = CorruptionField()
  private val props = mockk<PropInstances> {
    every { indices } returns (0 until 2)
    every { xAt(0) } returns 10.0
    every { yAt(0) } returns 10.0
    every { kindAt(0) } returns PropKind.TREE
    every { identityAt(0) } returns TREE
    every { xAt(1) } returns 20.0
    every { yAt(1) } returns 20.0
    every { kindAt(1) } returns PropKind.MANA_CRYSTAL
    every { identityAt(1) } returns CRYSTAL
    every { isBlighted(any()) } returns false
    every { isLarge(any()) } returns false
    every { subKindAt(any()) } returns 0
  }
  private val worldService = mockk<WorldService> {
    every { config.chunkSize } returns 32
    every { config.voxelSize } returns 1.0
    every { generated.propsIn(any(), any()) } returns PropInstances(0)
    every { generated.propsIn(0, 0) } returns props
  }
  private val tick = mockk<World>(relaxed = true)
  private val world = mockk<WorldView> {
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(tick) }
  }
  private val removal = mockk<PropRemoval>(relaxed = true)
  private val withered = mockk<StoredWitheredCellRepository>(relaxed = true) { every { findAll() } returns emptyList() }
  private val blight = WitheringBlight(
    WitheringConfig(),
    field,
    withered,
    worldService,
    world,
    mockk<WorldObjectResidencyService> { every { entitiesIn(any(), any()) } returns LongArray(0) },
    removal,
    mockk<WorldObjectDivergenceRegistry> { every { of(any()) } returns null },
  )

  @Test
  fun `the plants on rotten land die for good, once, and a crystal stays`() {
    field.change(ManaField.Cell(0, 0), 0.6)
    blight.killPlants()
    verify(exactly = 0) { removal.removedUnseen(any(), any(), any()) }

    field.change(ManaField.Cell(0, 0), 0.2)
    blight.killPlants()
    blight.killPlants()

    verify(exactly = 1) { removal.removedUnseen(TREE, any(), PropRemoval.Cause.GONE_FOR_GOOD) }
    verify(exactly = 0) { removal.removedUnseen(CRYSTAL, any(), any()) }
  }

  private companion object {
    const val TREE = 101L
    const val CRYSTAL = 102L
  }
}
