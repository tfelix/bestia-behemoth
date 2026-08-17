package net.bestia.worldgen.resource

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import net.bestia.worldgen.voxel.OreVeins
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ore deposits: the tonnage promise, the grade mix, and the block mapping.
 *
 * The first test is the reason this file exists. A deposit advertises tons of metal, and two different pieces
 * of code decide what that means: the world tier picks a body radius from the tonnage, and the chunk tier
 * fills that body voxel by voxel. Nothing but arithmetic keeps them agreeing, and "the world says fifty tons
 * and the ground holds five" is not a failure any other test would show - the world still generates, the
 * chunks still stream, the ore is still there.
 *
 * So the check is done the only way that actually proves it: sweep every voxel of a real orebody, add up what
 * each one drops, and compare the total against the marker.
 */
class OreDepositTest {

  private companion object {

    /**
     * Big enough to have mountains, and therefore to have mineralised ground at all.
     *
     * The same size and seed as `CivilisationStageTest` on purpose: it is already known to produce a world
     * with deposits in it, and a second lazily-built world of a different size would double the slowest part
     * of the suite for no extra coverage.
     */
    val world by lazy {
      StandardWorld.build(
        WorldConfig(seed = 0x50FA5L, widthCells = 288, heightCells = 288, chunkSize = 32, voxelSize = 1.0)
      )
    }

    val deposits by lazy {
      world.world.features.all()
        .filter { it.kind == FeatureKind.ORE_DEPOSIT }
        .filterIsInstance<PointMarker>()
    }

    fun typeOf(deposit: PointMarker) =
      ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]

    /** A deposit standing on its own, with every number chosen rather than drawn. */
    fun marker(type: ResourceType, radius: Double, richness: Double, depth: Double, tons: Double) = PointMarker(
      id = FeatureId(1),
      kind = FeatureKind.ORE_DEPOSIT,
      position = Vec2d(0.0, 0.0),
      attributes = StationTable.Builder(1)
        .channel(DepositChannels.TYPE) { type.ordinal.toDouble() }
        .channel(DepositChannels.RICHNESS) { richness }
        .channel(DepositChannels.TONS) { tons }
        .channel(DepositChannels.DEPTH) { depth }
        .channel(DepositChannels.RADIUS) { radius }
        .build()
    )
  }

  // --- The tonnage promise ---------------------------------------------------------------------------

  @Test
  fun `an orebody holds the tonnage its marker advertises`() {
    val grades = GradeMix()
    val radius = 60.0
    val richness = 0.4
    val depth = 100.0
    val surface = 500.0
    val voxelSize = 1.0

    val tons = OreBody.tonsOf(radius, richness, voxelSize, grades.meanYieldKg)
    val veins = OreVeins(
      listOf(marker(ResourceType.IRON, radius, richness, depth, tons)),
      seed = 0xB0DEL,
      grades = grades
    )

    var kilograms = 0.0
    forEachVoxelOf(radius, depth, surface) { x, y, z ->
      val block = veins.blockAt(x, y, z, surface) ?: return@forEachVoxelOf
      kilograms += grades.yieldKgOf(assertNotNull(OreBlocks.yieldOf(block)).grade)
    }

    val actual = kilograms / 1000.0
    assertTrue(
      abs(actual - tons) <= tons * 0.05,
      "the marker claims $tons t but sweeping every voxel of the body found $actual t. " +
          "The world tier and the chunk tier have stopped agreeing about the shape of an orebody"
    )
  }

  @Test
  fun `ore comes out five small to three medium to one rich`() {
    val grades = GradeMix()
    val radius = 60.0
    val richness = 0.4
    val veins = OreVeins(
      listOf(marker(ResourceType.IRON, radius, richness, 100.0, 40.0)),
      seed = 0x51EAL,
      grades = grades
    )

    val counts = HashMap<OreGrade, Int>()
    forEachVoxelOf(radius, depth = 100.0, surface = 500.0) { x, y, z ->
      val block = veins.blockAt(x, y, z, 500.0) ?: return@forEachVoxelOf
      val grade = assertNotNull(OreBlocks.yieldOf(block)).grade
      counts[grade] = (counts[grade] ?: 0) + 1
    }

    val total = counts.values.sum().toDouble()
    assertTrue(total > 10_000, "only $total ore voxels, too few to say anything about the mix")

    // Nine thousand-odd samples put the sampling error an order of magnitude below this, so a failure here is
    // a changed mix rather than an unlucky seed.
    for ((grade, expected) in listOf(OreGrade.SMALL to 5.0, OreGrade.MEDIUM to 3.0, OreGrade.RICH to 1.0)) {
      val share = (counts[grade] ?: 0) / total
      assertTrue(
        abs(share - expected / 9.0) < 0.01,
        "$grade is ${"%.3f".format(share)} of the ore, expected ${"%.3f".format(expected / 9.0)}"
      )
    }
  }

  @Test
  fun `a richer body is denser rather than wider`() {
    // The inverse relationship the solve rests on: hold the tonnage and double the concentration, and the
    // body must shrink. If radius stopped depending on richness this would silently keep passing tonnage
    // checks while every deposit came out the same size.
    val mean = GradeMix().meanYieldKg
    val lean = OreBody.radiusForTons(50.0, 0.2, 1.0, mean)
    val rich = OreBody.radiusForTons(50.0, 0.8, 1.0, mean)

    assertTrue(rich < lean, "a body at richness 0.8 ($rich m) is not smaller than one at 0.2 ($lean m)")
    assertEquals(
      50.0,
      OreBody.tonsOf(rich, 0.8, 1.0, mean),
      0.001,
      "solving for a radius and then back for the tonnage did not return the tonnage"
    )
  }

  // --- The block mapping -----------------------------------------------------------------------------

  @Test
  fun `every mineable ore has three distinct blocks that name it back`() {
    val seen = HashSet<BlockType>()

    for (ore in MinableOre.entries) {
      val blocks = assertNotNull(
        OreBlocks.blocksFor(ore.resource),
        "${ore.resource} is mineable but has no blocks to show as"
      )
      assertEquals(3, blocks.toSet().size, "${ore.resource} does not have three distinct grade blocks")

      for (grade in OreGrade.entries) {
        val block = assertNotNull(OreBlocks.blockFor(ore.resource, grade))
        assertTrue(seen.add(block), "$block is used by more than one ore")

        val back = assertNotNull(OreBlocks.yieldOf(block), "$block does not name what it yields")
        assertEquals(ore.resource, back.resource, "$block maps back to the wrong resource")
        assertEquals(grade, back.grade, "$block maps back to the wrong grade")
      }
    }
  }

  @Test
  fun `placer gold and lode gold are the same metal, and quarried stone is not ore`() {
    assertEquals(
      OreBlocks.blocksFor(ResourceType.GOLD_LODE),
      OreBlocks.blocksFor(ResourceType.GOLD_PLACER),
      "a player panning a river and a player in a shaft are holding different gold"
    )
    // The reverse map names the lode, so a gold block has one answer rather than two.
    assertEquals(
      ResourceType.GOLD_LODE,
      OreBlocks.yieldOf(BlockType.ORE_GOLD_RICH)?.resource
    )

    for (surface in listOf(ResourceType.STONE, ResourceType.TIMBER, ResourceType.FURS, ResourceType.FISH)) {
      assertNull(OreBlocks.blocksFor(surface), "$surface is not a thing in the rock")
      assertNull(OreBlocks.plainBlockFor(surface), "$surface is not a thing in the rock")
    }

    // Marble and clay do show, as plain material rather than as gradeable ore.
    assertEquals(BlockType.LIMESTONE, OreBlocks.plainBlockFor(ResourceType.MARBLE))
    assertEquals(BlockType.MUD, OreBlocks.plainBlockFor(ResourceType.CLAY))
    assertNull(OreBlocks.yieldOf(BlockType.LIMESTONE), "limestone is rock, not ore anybody picks up")
  }

  // --- The small-world floor -------------------------------------------------------------------------

  @Test
  fun `a world big enough for its ore is not touched by the small-world floor`() {
    val params = ResourceParams()

    // Exactly 1.0, not merely close to it: the point of the floor is that a full-size world generates the
    // terrain it would have generated without it, and `spacing * 0.9999` is a different world.
    for (edgeKm in listOf(384, 512, 1024, 4096)) {
      assertEquals(
        1.0,
        params.spacingShrink(edgeKm * 1000.0),
        0.0,
        "a $edgeKm km world does not need the floor, so it must be an exact no-op"
      )
    }
  }

  @Test
  fun `the floor gives a small world its ore without making the rare ones common`() {
    val params = ResourceParams()
    // The development world in the server's application.yml, which is where this problem was found.
    val shrink = params.spacingShrink(128_000.0)

    assertTrue(shrink < 1.0, "a 128 km world needs the floor, but it did nothing")

    val rarest = MinableOre.entries.maxBy { it.spacingFactor }
    val commonest = MinableOre.entries.minBy { it.spacingFactor }

    // The floor's own promise, read back off the result.
    val rarestSpacing = params.candidateSpacing * rarest.spacingFactor * shrink
    assertTrue(
      128_000.0 / rarestSpacing >= params.minSitesAcross - 1e-9,
      "${rarest.name} samples every ${rarestSpacing.toInt()} m, which is fewer than " +
          "${params.minSitesAcross} sites across a 128 km world - the floor did not lift it far enough"
    )

    // And the thing a per-ore clamp would have destroyed: scarcity is relative, so shrinking must not
    // compress the gap between the rarest ore and the commonest.
    val commonestSpacing = params.candidateSpacing * commonest.spacingFactor * shrink
    assertEquals(
      rarest.spacingFactor / commonest.spacingFactor,
      rarestSpacing / commonestSpacing,
      1e-9,
      "the floor changed how much rarer ${rarest.name} is than ${commonest.name}, " +
          "which would make gold as common as copper on exactly the worlds that needed help"
    )
  }

  // --- Placement on a real world ---------------------------------------------------------------------

  @Test
  fun `even the smallest world has every ore in it`() {
    // The requirement in one assertion. Built at the size and seed the zone server actually boots - 128 km,
    // `application.yml` - because that is the world this used to fail on: four or five of the seven ores were
    // absent outright, and no amount of walking would have found them.
    //
    // Its own world rather than the shared one above, and worth the couple of seconds: the property is about
    // *small* worlds specifically, and a 288 km world would pass it while the shipped one did not.
    val small = StandardWorld.build(
      WorldConfig(seed = 11753242L, widthCells = 128, heightCells = 128, chunkSize = 32, voxelSize = 1.0)
    )

    val found = small.world.features.all()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .groupBy { ResourceType.entries[it.attribute(DepositChannels.TYPE).toInt()] }

    // Per ore now rather than one number for all of them - see `MinableOre.guaranteedDeposits` for why the
    // staples are promised three and the luxuries one, and `OreCoverageTest` for the sweep that says the
    // promise holds on more than this one seed.
    for (ore in MinableOre.entries) {
      val promised = small.params.resource.ore.floorOf(ore)
      if (promised <= 0) continue

      val n = found[ore.resource].orEmpty().size
      assertTrue(
        n >= promised,
        "a 128 km world has $n ${ore.name} deposits, fewer than the $promised every world is promised - " +
            "a player could cross the whole map and prove the ore does not exist"
      )
    }
  }

  @Test
  fun `a world holds the tonnage of each ore that its area says it should`() {
    val area = world.config.widthMetres * world.config.heightMetres
    val byOre = deposits.groupBy { typeOf(it) }

    for (ore in MinableOre.entries) {
      // Only the ores with a floor under them. The abundance is what a world *should* hold given the geology,
      // and the floor is what makes it hold that much even when the sampler missed; an ore with no floor can
      // legitimately come out at zero tons, and asserting otherwise would be asserting the floor.
      if (ore.guaranteedDeposits <= 0) continue

      val held = byOre[ore.resource].orEmpty().sumOf { it.attribute(DepositChannels.TONS) }
      val target = ore.worldTons(area)

      // One-sided, and that is the model rather than a slack tolerance. The abundance is what the world
      // *should* hold; deposits are then floored at a size worth walking to, so a world whose share of an ore
      // is spread over more sites than it can fill comes out richer than its abundance - never poorer.
      assertTrue(
        held >= target * 0.98,
        "${ore.name}: the world holds ${held.toInt()} t against an abundance of ${target.toInt()} t"
      )
    }
  }

  @Test
  fun `every deposit sits at a depth its own ore allows`() {
    assertTrue(deposits.isNotEmpty(), "the world has no deposits at all")

    for (deposit in deposits) {
      val ore = MinableOre.of(typeOf(deposit)) ?: continue
      val depth = deposit.attribute(DepositChannels.DEPTH)

      assertTrue(
        depth >= ore.minDepth - 0.001 && depth <= ore.maxDepth + 0.001,
        "${ore.name} deposit ${deposit.id} is $depth m down, outside ${ore.minDepth}..${ore.maxDepth}"
      )
    }
  }

  /**
   * The two deep things lie below everything a civilisation digs, and diamond lies below mithrandium.
   *
   * This asserted a single ore at the bottom of the world until diamond arrived under it. The claim was never
   * really about mithrandium: it is that the world holds something **no settlement can reach**, so that there
   * is a reason to go and dig rather than to buy. Two such things is more of that property, not less - what
   * would break it is the gap closing, so the ordering below is checked in both places rather than relaxed
   * into "one of them is deepest".
   */
  @Test
  fun `the deep ores lie below everything a shaft reaches`() {
    val deep = setOf(ResourceType.MITHRANDIUM, ResourceType.DIAMOND)

    val mithrandium = deposits.filter { typeOf(it) == ResourceType.MITHRANDIUM }
    val diamond = deposits.filter { typeOf(it) == ResourceType.DIAMOND }
    val rest = deposits.filter { typeOf(it) !in deep }

    // Neither is asserted to exist: mithrandium needs old, hard, high crust all at once and diamond needs old,
    // flat crust away from a plate boundary, so a small test world may have neither. The claim being checked
    // is the ordering, which holds either way. `GemDepositTest` is what says they are reachable at all.
    val shallowestDeep = (mithrandium + diamond).minOfOrNull { it.attribute(DepositChannels.DEPTH) } ?: return
    val deepestOther = rest.maxOf { it.attribute(DepositChannels.DEPTH) }

    assertTrue(
      shallowestDeep > deepestOther,
      "the shallowest deep ore is $shallowestDeep m down but something else reaches $deepestOther m, " +
          "so nothing is out of reach of a medieval shaft any more"
    )

    // Asserted on the catalogue rather than on the placed deposits, because the two bands overlap on purpose -
    // 250..600 against 300..800 - so a single mithrandium can legitimately be found below a single diamond.
    // What must hold is that diamond *reaches* deeper than anything else, which is a fact about the table.
    assertTrue(
      MinableOre.DIAMOND.maxDepth > MinableOre.entries.filter { it != MinableOre.DIAMOND }.maxOf { it.maxDepth },
      "diamond is meant to reach deeper than anything else in the ground"
    )
  }

  @Test
  fun `no two mineable deposits sit on top of each other`() {
    val separation = world.params.resource.oreSeparation
    val mineable = deposits.filter { MinableOre.of(typeOf(it)) != null }

    assertTrue(mineable.size > 1, "too few mineable deposits to say anything about spacing")

    for (i in mineable.indices) {
      for (j in i + 1 until mineable.size) {
        val distance = mineable[i].position.distanceTo(mineable[j].position)
        assertTrue(
          distance >= separation * 0.98,
          "${typeOf(mineable[i])} and ${typeOf(mineable[j])} are ${distance.toInt()} m apart, " +
              "needing ${separation.toInt()} m - the dispersal pass let two bodies share a hillside"
        )
      }
    }
  }

  /**
   * Visits the centre of every voxel that could be inside a body of this size, at one metre spacing.
   *
   * Sampling centres rather than corners, because that is what `ChunkMaterializer` asks `OreVeins` about.
   */
  private fun forEachVoxelOf(radius: Double, depth: Double, surface: Double, visit: (Double, Double, Double) -> Unit) {
    val halfHeight = radius * OreBody.VERTICAL_FLATTENING
    val centre = surface - depth
    val span = radius.toInt() + 1

    for (z in (centre - halfHeight).toInt() - 1..(centre + halfHeight).toInt() + 1) {
      for (y in -span..span) {
        for (x in -span..span) {
          visit(x + 0.5, y + 0.5, z + 0.5)
        }
      }
    }
  }
}
