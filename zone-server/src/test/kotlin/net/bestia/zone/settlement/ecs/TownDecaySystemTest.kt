package net.bestia.zone.settlement.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PropKindDto
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test

class TownDecaySystemTest {

  private val office = StructureEntry(42L, StaticEntityKind.POST_OFFICE, 3L, 7L, Vec3L(100, 100, 0), 0f)
  private val workbench = StructureEntry(43L, StaticEntityKind.WORKBENCH, 3L, 7L, Vec3L(100, 100, 0), 0f)
  private var standing = false

  private val towns = mockk<TownRegistry> { every { standingAt(any()) } answers { if (standing) mockk() else null } }
  private val structures = mockk<PlayerStructureRegistry>(relaxed = true) {
    every { all() } returns listOf(office, workbench)
  }
  private val propKinds = mockk<PropKindRegistry> {
    every { of(any()) } returns PropKindDto(kind = StaticEntityKind.POST_OFFICE, maxHp = 800)
  }
  private val worldService = mockk<WorldService> {
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
  }
  private val system = TownDecaySystem(towns, structures, propKinds, mockk(relaxed = true), mockk(relaxed = true), worldService)

  @Test
  fun `a post office outside a town loses half its health in 48 h`() {
    system.update(testWorld(), 48 * 3600f)

    verify { structures.setWear(42L, 400, 0) }
    verify(exactly = 0) { structures.setWear(43L, any(), any()) }
  }

  @Test
  fun `a post office outside a town breaks in 96 h`() {
    system.update(testWorld(), 96 * 3600f)

    verify { structures.remove(42L, 3, 3) }
  }

  @Test
  fun `a post office in a standing town does not decay`() {
    standing = true

    system.update(testWorld(), 96 * 3600f)

    verify(exactly = 0) { structures.setWear(any(), any(), any()) }
    verify(exactly = 0) { structures.remove(any(), any(), any()) }
  }
}
