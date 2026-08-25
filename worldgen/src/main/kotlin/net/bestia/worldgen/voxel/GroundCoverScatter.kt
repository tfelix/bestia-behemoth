package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Quantize

/** Tuning for [GroundCoverScatter]. */
data class GroundCoverParams(

  /**
   * Edge of the lattice square that holds at most one plant, in metres.
   *
   * The **decision unit**, for the reason `VegetationParams.cellSize` is one: a smooth probability sampled
   * per column is still a coin flip per column, and that reads as speckle rather than as ground.
   *
   * Three metres, so this is the finest lattice in the chunk tier - a herb is a thing you step over where a
   * tree is a thing you walk around. Distinct from the tree lattice's four metres and the crystal's eleven so
   * that the scatters interleave rather than landing a herb inside a trunk.
   *
   * **Not a thing to change after a world ships**: a cell index is the durable half of a prop's identity, so
   * moving this renames every plant in the world. See [PropId].
   */
  val cellSize: Double = 3.0,

  /** How far a plant may wander from its cell's centre, as a share of [cellSize]. `VegetationParams`' trade. */
  val jitterShare: Double = 0.7,

  /**
   * Wavelength of the patch field in metres: how far it is across a stand of herbs.
   *
   * An order of magnitude below `VegetationParams.patchWavelength`, because this is a different-sized thing.
   * A wood is a hundred metres across and a patch of herbs is a few paces, so a shared field would give a
   * meadow one verdict for its whole width.
   */
  val patchWavelength: Double = 26.0,

  /** Patch value below which nothing grows, so a meadow has bare ground in it rather than an even stipple. */
  val patchCutoff: Double = 0.35,

  /** Width of the ramp above [patchCutoff], so a patch has an edge rather than a wall. */
  val patchSoftness: Double = 0.25,

  /**
   * Soil depth in metres at which the soil term stops limiting anything.
   *
   * Half `VegetationParams.soilDepthFull`, and the difference is the point: a herb roots in what a tree
   * cannot, so thin soil over rock carries ground cover long after it has stopped carrying a wood.
   */
  val soilDepthFull: Double = 0.5,

  /**
   * The one gain on [Biome.litter], and the knob to turn when a world has too few plants or too many.
   *
   * Everything else in the density is a claim about the world - which biome, how deep the soil, whether this
   * is a patch - so the single free scalar lives here rather than being spread over three of them.
   *
   * **Set against the tree density rather than freely**, because every prop is a real entity: residency calls
   * `world.createEntity` per site for every column a player holds, so this number is an entity budget and not
   * only a look. `VegetationScatter` measures 24 tree props per hectare over all land, and this is tuned to
   * about twice that in grassland - the ground has things on it, at the same order as the trees do. Doubling
   * it again is a plausible-looking change that triples the static entity count in a view volume.
   */
  val litterGain: Double = 0.12,

  /**
   * How much a closed canopy suppresses the layer under it, as a share of [Biome.canopy].
   *
   * Damping rather than a gate: a forest floor is not bare, and a rate that went to zero under trees would
   * make woodland herbs - the ones worth looking for - impossible to find.
   */
  val canopyDamping: Double = 0.5,

  /**
   * Cap on the per-cell plant probability.
   *
   * Load bearing as an *optimisation* as well as a limit: the cheap reject in [GroundCoverScatter] tests one
   * hash against this before evaluating the density field, which is only sound because nothing may exceed it.
   */
  val maxDensity: Double = 0.25,

  /**
   * Share of plants that come out woody, as a multiple of [Biome.canopy], capped at [maxShrubShare].
   *
   * Canopy is the axis because it is already the measure of how woody a biome is: grassland at 0.05 gets the
   * odd bush in a meadow of herbs, dryland at 0.11 comes out visibly scrubby, and a forest floor is mostly
   * understory. One number instead of a per-biome table, and it reads off a column the biome table already
   * has to justify.
   */
  val shrubCanopyGain: Double = 2.5,

  /** Ceiling on the shrub share, so even a closed wood keeps some herbs on its floor. */
  val maxShrubShare: Double = 0.7,

  /**
   * How far above the water surface a plant may stand and still be a reed, in metres.
   *
   * The height over the water rather than the biome, deliberately: a biome is a kilometre cell and a pond
   * edge is not. A shallow shore therefore grows a wide band of reed and a steep bank grows a line of it,
   * which is what those two shores actually look like.
   */
  val reedMargin: Double = 0.8,

  /** Nominal height of each kind, in metres, before [heightSpread]. */
  val herbHeight: Double = 0.45,
  val shrubHeight: Double = 1.3,
  val reedHeight: Double = 1.8,

  /**
   * Half-width of the size spread around a kind's nominal height, as a share of it.
   *
   * One number for the three kinds rather than a min and a max each. The three heights differ by a factor of
   * four and the *spread* around each is the same relative thing, so six fields would be four of them saying
   * the same number twice.
   */
  val heightSpread: Double = 0.3
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(patchWavelength > 0.0) { "patchWavelength must be positive, was $patchWavelength" }
    require(patchCutoff in 0.0..1.0) { "patchCutoff must be in [0,1], was $patchCutoff" }
    require(patchSoftness > 0.0) { "patchSoftness must be positive, was $patchSoftness" }
    require(soilDepthFull > 0.0) { "soilDepthFull must be positive, was $soilDepthFull" }
    require(litterGain > 0.0) { "litterGain must be positive, was $litterGain" }
    require(canopyDamping in 0.0..1.0) { "canopyDamping must be in [0,1], was $canopyDamping" }
    require(maxDensity > 0.0 && maxDensity <= 1.0) { "maxDensity must be in (0,1], was $maxDensity" }
    require(shrubCanopyGain >= 0.0) { "shrubCanopyGain must not be negative, was $shrubCanopyGain" }
    require(maxShrubShare in 0.0..1.0) { "maxShrubShare must be in [0,1], was $maxShrubShare" }
    require(reedMargin >= 0.0) { "reedMargin must not be negative, was $reedMargin" }
    require(herbHeight > 0.0) { "herbHeight must be positive, was $herbHeight" }
    require(shrubHeight > 0.0) { "shrubHeight must be positive, was $shrubHeight" }
    require(reedHeight > 0.0) { "reedHeight must be positive, was $reedHeight" }
    require(heightSpread in 0.0..1.0) { "heightSpread must be in [0,1], was $heightSpread" }
  }

  override fun digest(): ParamsDigest {
    return ParamsDigest()
      .put("cellSize", cellSize)
      .put("jitterShare", jitterShare)
      .put("patchWavelength", patchWavelength)
      .put("patchCutoff", patchCutoff)
      .put("patchSoftness", patchSoftness)
      .put("soilDepthFull", soilDepthFull)
      .put("litterGain", litterGain)
      .put("canopyDamping", canopyDamping)
      .put("maxDensity", maxDensity)
      .put("shrubCanopyGain", shrubCanopyGain)
      .put("maxShrubShare", maxShrubShare)
      .put("reedMargin", reedMargin)
      .put("herbHeight", herbHeight)
      .put("shrubHeight", shrubHeight)
      .put("reedHeight", reedHeight)
      .put("heightSpread", heightSpread)
  }
}

/**
 * The plants a player can walk up to and pick: herbs, shrubs and reeds.
 *
 * Implicit, like the trees and the crystals, and for the same reason - there are far too many to store and a
 * hash answers the question. `voxel/VegetationScatter.kt` makes that argument in full; this is one order of
 * magnitude down from it.
 *
 * ### This is not the grass you see
 *
 * The load-bearing distinction in the file. Grass as a *surface* is the terrain's own material and burns as a
 * field - `zone-server`'s `BurnableGround` reads the cap block, with no entity anywhere - and the tufts a
 * client scatters for looks are the client's own arithmetic over the mesh it just built. What is here is the
 * sparse, **interactable** third of that: the plants that have an identity, health, a `collect` yield and a
 * divergence row when one is picked.
 *
 * So the densities below are deliberately far too low to read as ground cover, and reading them as a bug is
 * the mistake to avoid. A stand of reeds you can cut every twelve metres is right; a reed every metre would be
 * a thousand entities in a view volume, which is the cost `chunk_static_entities_smsg.proto` measured and
 * refused.
 *
 * ### One lattice, three kinds
 *
 * The three kinds are near enough mutually exclusive - a reed grows where a herb drowns - so one lattice with
 * the kind chosen by the ground says the same thing as three lattices while pinning one [cellSize] instead of
 * three. It also makes overlap impossible rather than merely unlikely: a cell holds one plant, so a shrub
 * cannot stand inside a reed.
 *
 * Sharing is safe here where it is not between `MANA_CRYSTAL` and `WOUND_SPIRE`: [PropId] packs the kind
 * beside the cell, so two *different* kinds on one lattice still get different names. What that KDoc warns
 * about is two *lattices* of one kind.
 *
 * ### The kind depends on the ground, and that is still a pure function of position
 *
 * A reed is decided by how far the ground stands above the water over it, which means the kind - and therefore
 * the [PropId] - is not known from the cell index alone. That is sound because [PropSite] is required to be a
 * pure function of the prop's own position, so any chunk asking about this cell reaches the same verdict. Only
 * the owning chunk emits it in any case.
 *
 * ### Not tuned by argument
 *
 * The rates below are measurements. `GroundCoverScatterTest` prints plants per hectare binned by the biome
 * under each plant, and Phase 7's droplet density - set twenty times too high by a plausible-sounding
 * argument - is the standing reason not to do it the other way round. Measured on seed 7:
 *
 * ```
 * biome                      herb    shrub     reed    total
 * GRASSLAND                  41.3      5.9      0.0     47.1
 * TEMPERATE_FOREST           10.3     22.9      0.0     33.2
 * DRYLAND                    12.9      4.5      0.1     17.5
 * DESERT                      1.3      0.0      0.0      1.3
 * ICE_SHEET                   0.0      0.0      0.0      0.0
 * ```
 *
 * A meadow is herbs with the odd bush in it and a forest floor is mostly understory, which is
 * [GroundCoverParams.shrubCanopyGain] doing its job. The desert is not zero and cannot be - see the test.
 *
 * Reeds are rare in that table and that is the shore rather than the reed: only 12 per cent of the *shore*
 * plants on this world are anything else, and there is very little shoreline in a random sample of ground.
 */
class GroundCoverScatter(
  private val config: WorldConfig,

  /**
   * The surface classifier, shared with the materialiser rather than rebuilt, for the reason
   * [VegetationScatter] shares it: two objects built from the same layers agree, two built separately agree
   * until one of them gains a layer.
   */
  private val surface: SurfaceSampler,
  seed: Long,
  private val params: GroundCoverParams = GroundCoverParams()
) {

  private val cellUnits = Quantize.toFixed(params.cellSize)
  private val plantSeed = GenRng.mix64(seed xor PLANT_SALT)
  private val patchSeed = GenRng.mix64(seed xor PATCH_SALT)

  private val jitter = params.cellSize * params.jitterShare

  /**
   * The chance that one cell at this position holds a plant, in `[0, maxDensity]`.
   *
   * **[Biome.litter], where `VegetationScatter.densityAt` refuses it, and both are right.** Litter is dead
   * organic matter - a fertility term - so reusing it for trees would have wooded every prairie, grassland
   * being one of the best litter producers on the table. The ground layer is exactly what that fertility
   * grows, so here it is the correct signal and canopy is the wrong one.
   *
   * Canopy still appears, as damping: the layer sits *under* the trees, and a closed wood shades its own
   * floor without clearing it.
   */
  fun densityAt(worldX: Double, worldY: Double): Double {
    val biome = surface.biomeAt(worldX, worldY)
    if (biome.litter <= 0.0) return 0.0

    val soil = PolylineFeature.smoothstep(surface.soilDepthAt(worldX, worldY) / params.soilDepthFull)
    if (soil <= 0.0) return 0.0

    val open = 1.0 - biome.canopy * params.canopyDamping
    val density = biome.litter * params.litterGain * open * soil * patchAt(worldX, worldY)

    return density.coerceAtMost(params.maxDensity)
  }

  /** The smooth field that decides where a patch is, in `[0,1]`, and zero on the bare ground between them. */
  fun patchAt(worldX: Double, worldY: Double): Double {
    val raw = (Noise.fbm(
      patchSeed, worldX / params.patchWavelength, worldY / params.patchWavelength, PATCH_OCTAVES
    ) + 1.0) * 0.5

    return PolylineFeature.smoothstep((raw - params.patchCutoff) / params.patchSoftness)
  }

  /**
   * The plants standing inside one chunk, as props for a runtime to make entities of.
   *
   * No halo and no candidate buffer: a plant is a point that reaches nothing, so nothing outside this chunk
   * can contribute one. The cell range is the cells overlapping the chunk with no expansion, which is sound
   * because jitter cannot carry a plant out of its own cell - [GroundCoverParams.jitterShare] is at most one.
   *
   * Ownership is the plant's voxel column in integers, for the reason `VegetationScatter.propsIn` gives: a
   * bounds test on a closed interval hands a plant exactly on a chunk boundary to both of the chunks that
   * share it.
   *
   * @param site ground elevation, and `NaN` where another producer in the chunk tier has already claimed the
   *   spot. Shared with the trees, so a herb inherits the street, bridge, building and cave vetoes for free -
   *   which is what keeps a plant off a paved road.
   */
  fun propsIn(chunk: ChunkPos, site: PropSite, into: PropInstances) {
    val bounds = config.chunkBounds(chunk)
    val fromX = cellOf(bounds.minX)
    val untilX = cellOf(bounds.maxX) + 1
    val fromY = cellOf(bounds.minY)
    val untilY = cellOf(bounds.maxY) + 1

    for (cellY in fromY until untilY) {
      for (cellX in fromX until untilX) {
        val plant = plantAt(cellX, cellY) ?: continue

        if (config.chunkOf(plant.x) != chunk.x) continue
        if (config.chunkOf(plant.y) != chunk.y) continue

        val ground = site.groundAt(plant.x, plant.y)
        if (ground.isNaN()) continue

        // Nothing grows out of standing water. The biome term already refuses OCEAN and LAKE - both score
        // zero on litter - but a biome is a kilometre cell and a pond edge is not.
        val waterLevel = surface.waterLevelAt(plant.x, plant.y)
        if (waterLevel > ground) continue

        val blighted = surface.isBlightedAt(plant.x, plant.y)
        val biome = surface.biomeAt(plant.x, plant.y)

        // Nor out of ice or year-round snow, asked of the cap block for the reason a trunk asks it there: the
        // one place deciding what the top of a column is made of also decides what can root in it. This is
        // ahead of the reed test on purpose - a frozen shore has no reeds either.
        val cap = SurfaceCover.cap(biome, surface.temperatureAt(plant.x, plant.y), 0.0, blighted)
        if (cap == BlockType.ICE || cap == BlockType.SNOW) continue

        val (kind, nominalHeight) = when {
          ground - waterLevel <= params.reedMargin -> PropKind.REED to params.reedHeight
          plant.shrubRoll < shrubShareOf(biome) -> PropKind.SHRUB to params.shrubHeight
          else -> PropKind.HERB to params.herbHeight
        }

        into.add(
          kind = kind,
          identity = PropId.of(kind, cellX, cellY),
          x = plant.x,
          y = plant.y,
          ground = ground,
          heightM = nominalHeight * (1.0 + (plant.sizeRoll - 0.5) * 2.0 * params.heightSpread),
          // No radius: a herb has no spread worth sending, and `radiusAt` is a *crown* everywhere else.
          flags = if (blighted) PropFlags.BLIGHTED else 0
        )
      }
    }
  }

  /**
   * The plant in one lattice cell, or null for an empty cell.
   *
   * One hash per cell, walked with [GenRng.mix64] for the further draws rather than re-hashed: a chunk visits
   * a thousand cells at this spacing and a world visits rather more.
   *
   * Judged where the plant stands rather than at the cell centre, so one jittered onto a river bank is on the
   * river bank.
   */
  private fun plantAt(cellX: Long, cellY: Long): Plant? {
    val key = GenRng.hash(plantSeed, cellX, cellY)
    val roll = GenRng.unit(key)
    // Cheap reject before the density field, sound only because densityAt is capped at the same value.
    if (roll >= params.maxDensity) return null

    val jitterX = (GenRng.unit(GenRng.mix64(key + 1)) - 0.5) * jitter
    val jitterY = (GenRng.unit(GenRng.mix64(key + 2)) - 0.5) * jitter
    val x = (cellX + 0.5) * params.cellSize + jitterX
    val y = (cellY + 0.5) * params.cellSize + jitterY

    if (roll >= densityAt(x, y)) return null

    return Plant(
      x = x,
      y = y,
      shrubRoll = GenRng.unit(GenRng.mix64(key + 3)),
      sizeRoll = GenRng.unit(GenRng.mix64(key + 4))
    )
  }

  /** How much of a biome's ground cover comes out woody rather than herbaceous. */
  private fun shrubShareOf(biome: Biome): Double {
    return (biome.canopy * params.shrubCanopyGain).coerceAtMost(params.maxShrubShare)
  }

  /** Lattice cell index of a world coordinate. Integer division of a fixed-point value. */
  private fun cellOf(world: Double): Long {
    return Math.floorDiv(Quantize.toFixed(world), cellUnits)
  }

  /** One plant, as the lattice draws it before the ground is known. */
  private class Plant(val x: Double, val y: Double, val shrubRoll: Double, val sizeRoll: Double)

  companion object {

    /** The kinds this emits, for a consumer counting or filtering ground cover. */
    val KINDS = setOf(PropKind.HERB, PropKind.SHRUB, PropKind.REED)

    private const val PLANT_SALT = 0x47726F756E6443L
    private const val PATCH_SALT = 0x506174636847L

    /**
     * Two, for `VegetationScatter.PATCH_OCTAVES`' reason one scale down: a patch wants a ragged edge rather
     * than a fractal one, and a third octave at twenty-six metres varies inside a single plant.
     */
    private const val PATCH_OCTAVES = 2
  }
}
