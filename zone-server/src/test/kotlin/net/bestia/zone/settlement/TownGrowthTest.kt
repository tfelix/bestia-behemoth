package net.bestia.zone.settlement

import net.bestia.zone.entity.StaticEntityKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TownGrowthTest {

  @Test
  fun `a bare sign spans the base sphere`() {
    assertEquals(TownGrowth.Size(0.0, 150.0), TownGrowth.sizeOf(emptyList()))
  }

  @Test
  fun `a building outside the sphere does not count`() {
    val size = TownGrowth.sizeOf(listOf(TownGrowth.Building(StaticEntityKind.WORKBENCH, 160.0)))

    assertEquals(0.0, size.points)
  }

  @Test
  fun `a building on the edge pushes the edge out to the next one`() {
    val size = TownGrowth.sizeOf(
      listOf(
        TownGrowth.Building(StaticEntityKind.WORKBENCH, 140.0),
        TownGrowth.Building(StaticEntityKind.FORGE, 170.0),
      )
    )

    assertEquals(TownGrowth.Size(2.0, 200.0), size)
  }

  @Test
  fun `ten wall pieces are worth one point and a campfire none`() {
    val walls = List(10) { TownGrowth.Building(StaticEntityKind.STRUCTURE_WALL, 10.0) }
    val size = TownGrowth.sizeOf(walls + TownGrowth.Building(StaticEntityKind.CAMPFIRE, 10.0))

    assertEquals(1.0, size.points, 1e-9)
  }

  @Test
  fun `the sphere and the sign stop growing at their caps`() {
    val stations = List(100) { TownGrowth.Building(StaticEntityKind.WORKBENCH, 10.0) }
    val size = TownGrowth.sizeOf(stations)

    assertEquals(500.0, size.radiusMetres)
    assertEquals(30_000, TownGrowth.signMaxHp(size.points))
    assertEquals(60, TownGrowth.signDefense(size.points))
  }

  @Test
  fun `each point adds 500 hp and 2 defense`() {
    assertEquals(3_000, TownGrowth.signMaxHp(2.0))
    assertEquals(14, TownGrowth.signDefense(2.0))
  }
}
