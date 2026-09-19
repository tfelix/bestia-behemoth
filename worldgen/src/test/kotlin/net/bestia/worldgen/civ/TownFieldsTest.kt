package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The worked ground around a settlement.
 *
 * What has to hold is that a field is outside the built edge, that it is on ground the settlement could
 * actually work, and that neighbouring fields do not share a furrow bearing - the last being the whole reason
 * the bearing is stored rather than derived from the settlement.
 */
class TownFieldsTest {

  private val centre = Vec2d(10_000.0, 10_000.0)
  private val params = TownParams()

  private fun roll(seed: Long): (Long, Long) -> Double {
    return townRoll(GenRng.hashString("fields-$seed"), 0)
  }

  private fun frame(radius: Double, seed: Long, buildable: (Vec2d) -> Boolean = { true }): TownFrame {
    return TownFrame(
      centre = centre,
      builtRadius = radius,
      boundary = TownBoundary.of(
        centre = centre,
        builtRadius = radius,
        axis = Vec2d(1.0, 0.0),
        aspect = TownBoundary.aspectOf(roll(seed), params.streets),
        seed = seed,
        params = params.streets
      ),
      groundAt = { 100.0 },
      buildable = buildable,
      approaches = emptyList()
    )
  }

  private fun fields(
    radius: Double = 180.0,
    wanted: Int = 12,
    seed: Long = 1L,
    buildable: (Vec2d) -> Boolean = { true }
  ): List<AreaFeature> {
    var next = 0L
    val f = frame(radius, seed, buildable)
    return TownFields.of(
      frame = f,
      built = f.boundary,
      reach = TownFields.reachFor(radius),
      wanted = wanted,
      channels = emptyList(),
      settlement = 3,
      roll = roll(seed),
      nextId = { FeatureId(++next) }
    ).filterIsInstance<AreaFeature>()
  }

  @Test
  fun `a settlement gets fields, and they are fields`() {
    val fields = fields()
    assertTrue(fields.size >= 4, "a settlement of this size works several fields, got ${fields.size}")
    assertTrue(fields.all { it.kind == FeatureKind.FIELD })
    assertTrue(
      fields.all { it.attribute(FieldChannels.SETTLEMENT).toInt() == 3 },
      "every field names the settlement that works it"
    )
  }

  @Test
  fun `nothing is sown inside the built edge`() {
    val f = frame(180.0, 1L)
    for (field in fields()) {
      assertTrue(
        !f.boundary.contains(field.ring.centroid),
        "a field is centred inside the settlement at ${field.ring.centroid}"
      )
    }
  }

  @Test
  fun `neighbouring fields are ploughed different ways`() {
    val bearings = fields().map { it.attribute(FieldChannels.BEARING) }
    assertTrue(bearings.size >= 4)
    // Distinct to a degree. One bearing for every field is a texture, not a patchwork.
    assertTrue(
      bearings.map { Math.round(it * 57.3) }.distinct().size >= bearings.size / 2,
      "fields share too many bearings: $bearings"
    )
  }

  @Test
  fun `ground the settlement cannot work grows nothing`() {
    // Everything east of the centre is water, so no field may be centred there.
    val east = fields(buildable = { it.x < centre.x })
    assertTrue(east.isNotEmpty(), "the western half still grows something")
    assertTrue(
      east.all { it.ring.centroid.x < centre.x + params.lotDepth },
      "a field was sown on ground the settlement cannot work"
    )
  }

  @Test
  fun `the layout is a pure function of the seed`() {
    val once = fields(seed = 5L)
    val twice = fields(seed = 5L)
    assertEquals(once.size, twice.size)
    for (i in once.indices) {
      assertEquals(once[i].ring.centroid, twice[i].ring.centroid)
      assertEquals(once[i].attribute(FieldChannels.BEARING), twice[i].attribute(FieldChannels.BEARING))
    }
  }
}
