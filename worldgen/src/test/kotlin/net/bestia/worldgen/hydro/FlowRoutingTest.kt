package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowRoutingTest {

  private val metres = 1000.0

  /** A plane tilting down towards +x, with an ocean strip along the eastern edge. */
  private fun ramp(width: Int, height: Int, oceanColumns: Int = 2) = Grid(width, height) { x, _ ->
    if (x >= width - oceanColumns) -50.0 else (width - oceanColumns - x) * 20.0
  }

  @Test
  fun `every cell drains somewhere after the fill`() {
    // The property the whole stage exists to guarantee. Without it rivers end in the middle of nowhere,
    // which is the single most common way a generator's hydrology is visibly wrong.
    val elevation = ramp(20, 12)
    elevation[8, 5] = -400.0
    elevation[9, 5] = -380.0
    elevation[8, 6] = -420.0

    val network = FlowRouting.solve(elevation, seaLevel = 0.0, metresPerCell = metres)

    for (i in 0 until network.size) {
      var current = i
      var steps = 0
      while (!network.isOutlet(current)) {
        current = network.receiver[current]
        assertTrue(steps++ < network.size, "cell $i does not reach an outlet")
      }
    }
  }

  @Test
  fun `the fill raises a pit to its spill level and records the depth`() {
    val elevation = ramp(20, 12)
    elevation[8, 5] = -400.0

    val network = FlowRouting.solve(elevation, seaLevel = 0.0, metresPerCell = metres)
    val pit = network.index(8, 5)

    assertTrue(network.fillDepth.data[pit] > 300.0, "the pit was barely filled: ${network.fillDepth.data[pit]}")
    assertTrue(network.filled.data[pit] > elevation.data[pit])

    // Cells that were never in a depression must be left exactly alone, epsilon included.
    val slope = network.index(3, 3)
    assertEquals(0.0, network.fillDepth.data[slope], 1e-12)
  }

  @Test
  fun `the ocean is only water connected to the edge of the world`() {
    // The distinction between the Dead Sea and the Mediterranean. Treating any below-sea-level cell as
    // ocean floods a continent through a hole that does not exist.
    val elevation = Grid(15, 15) { _, _ -> 200.0 }
    for (y in 0 until 15) {
      elevation[0, y] = -100.0
      elevation[1, y] = -100.0
    }
    elevation[9, 9] = -300.0

    val ocean = FlowRouting.oceanMask(elevation, seaLevel = 0.0)

    assertTrue(ocean[9 * 15 + 0], "the strip touching the edge is sea")
    assertFalse(ocean[9 * 15 + 9], "an isolated below-sea-level basin is not sea")
  }

  @Test
  fun `flow directions point downhill and outlets have none`() {
    val elevation = ramp(16, 10)
    val network = FlowRouting.solve(elevation, 0.0, metres)

    for (y in 1 until 9) {
      for (x in 1 until 15) {
        val i = network.index(x, y)
        if (network.isOutlet(i)) {
          assertEquals(D8.NONE, network.direction[i], "outlet at ($x,$y) has a direction")
          continue
        }
        assertTrue(
          network.filled.data[network.receiver[i]] < network.filled.data[i],
          "($x,$y) drains uphill"
        )
      }
    }
  }

  @Test
  fun `the drainage stack lists every cell after the cell it drains into`() {
    // The invariant that makes both flow accumulation and the implicit stream power solve single-pass.
    val elevation = ramp(18, 14)
    elevation[6, 7] = -200.0
    val network = FlowRouting.solve(elevation, 0.0, metres)

    val position = IntArray(network.size)
    for (k in network.stack.indices) position[network.stack[k]] = k

    assertEquals(network.size, network.stack.toSet().size, "the stack must contain every cell once")
    for (i in 0 until network.size) {
      val r = network.receiver[i]
      if (r != i) {
        assertTrue(position[r] < position[i], "cell $i appears before its receiver $r")
      }
    }
  }

  @Test
  fun `accumulation conserves the total and grows downstream`() {
    val elevation = ramp(20, 16)
    val network = FlowRouting.solve(elevation, 0.0, metres)

    val accumulated = network.accumulate { 1.0 }

    // Everything that fell on the map has to end up at an outlet.
    var atOutlets = 0.0
    for (i in 0 until network.size) {
      if (network.isOutlet(i)) atOutlets += accumulated.data[i]
    }
    assertEquals(network.size.toDouble(), atOutlets, 1e-6)

    // And it must never shrink on the way there - the regression invariant the architecture document
    // asks for by name.
    for (i in 0 until network.size) {
      val r = network.receiver[i]
      if (r != i) {
        assertTrue(
          accumulated.data[r] >= accumulated.data[i] - 1e-9,
          "accumulation fell from ${accumulated.data[i]} to ${accumulated.data[r]}"
        )
      }
    }
  }

  @Test
  fun `routing is a pure function of the surface`() {
    val elevation = ramp(24, 18)
    elevation[10, 9] = -120.0

    val once = FlowRouting.solve(elevation.copy(), 0.0, metres)
    val twice = FlowRouting.solve(elevation.copy(), 0.0, metres)

    assertTrue(once.receiver.contentEquals(twice.receiver))
    assertTrue(once.direction.contentEquals(twice.direction))
    assertTrue(once.stack.contentEquals(twice.stack))
  }

  @Test
  fun `a wet basin overflows and a dry one becomes a salt lake`() {
    // The endorheic case is the whole reason lake levels are computed rather than assumed. A basin always
    // filled to its rim means every lake in the world has an outlet river and salt lakes do not exist.
    val elevation = Grid(21, 21) { _, _ -> 400.0 }
    for (y in 0 until 21) {
      elevation[0, y] = -50.0
      elevation[20, y] = -50.0
    }
    for (y in 7..13) {
      for (x in 7..13) {
        elevation[x, y] = 100.0
      }
    }

    val network = FlowRouting.solve(elevation, 0.0, metres)

    val wet = Lakes.identify(
      network, elevation, Grid(21, 21, 500.0), seaLevel = 0.0, evaporationDepth = 1.0
    )
    val dry = Lakes.identify(
      network, elevation, Grid(21, 21, 0.001), seaLevel = 0.0, evaporationDepth = 4.0
    )

    assertEquals(1, wet.lakeCount)
    assertEquals(0, wet.endorheicCount, "a basin with 500 cubic metres a second arriving must overflow")
    assertEquals(1, dry.endorheicCount, "a basin with almost no inflow must evaporate below its rim")
    assertTrue(
      dry.basins[0].surfaceLevel < wet.basins[0].surfaceLevel,
      "the dry lake should stand lower than the wet one"
    )
  }

  @Test
  fun `lake ids are negative for endorheic basins and the surface is NaN on dry land`() {
    val elevation = Grid(15, 15) { _, _ -> 300.0 }
    for (y in 0 until 15) elevation[0, y] = -20.0
    for (y in 6..8) {
      for (x in 6..8) elevation[x, y] = 60.0
    }

    val network = FlowRouting.solve(elevation, 0.0, metres)
    val lakes = Lakes.identify(
      network, elevation, Grid(15, 15, 0.0005), seaLevel = 0.0, evaporationDepth = 5.0
    )

    // The floor of this basin is dead flat, which is the case that broke an earlier version: with the
    // water line decided by comparing each cell's elevation against the level, a flat floor floods either
    // every cell or none, and the lake vanished entirely. Flooding by rank instead always leaves a pond.
    val basin = lakes.basins.single()
    assertEquals(9, basin.cells.size, "the depression is nine cells")
    assertEquals(1, basin.area, "a basin with almost no inflow should hold water in one of them")

    val wet = basin.floodedCells.single()
    assertTrue(lakes.lakeId.data[wet] < 0, "an endorheic basin should be labelled negative")
    assertTrue(
      lakes.surface.data[wet] > elevation.data[wet],
      "the water surface must stand above the bed it covers"
    )

    // And the rest of the depression is dry ground, not lake bed.
    for (cell in basin.cells) {
      if (cell == wet) continue
      assertEquals(0, lakes.lakeId.data[cell], "cell $cell is above the water line")
    }

    val hillside = network.index(12, 12)
    assertEquals(0, lakes.lakeId.data[hillside])
    assertTrue(lakes.surface.data[hillside].isNaN(), "dry land must have no water level, not zero")
  }
}
