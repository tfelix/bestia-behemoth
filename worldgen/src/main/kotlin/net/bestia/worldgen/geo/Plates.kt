package net.bestia.worldgen.geo

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.PointIndex
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class PlateType { OCEANIC, CONTINENTAL }

/**
 * How two plates meet.
 *
 * The whole reason to start from plates rather than from fractal noise is that this classification
 * exists: it is what makes a mountain range have a *reason*, and what puts a trench on one side of an
 * arc and foothills on the other. Fractal noise gives you the same terrain everywhere, and players
 * read that as "generated" within about a minute of walking.
 */
enum class BoundaryType { CONVERGENT, DIVERGENT, TRANSFORM }

data class Plate(
  val id: Int,
  val seed: Vec2d,
  val type: PlateType,
  /** Direction and relative speed of drift; magnitude is in the same arbitrary unit for all plates. */
  val drift: Vec2d,
  /** Elevation of the plate's undisturbed interior, in metres. */
  val baseElevation: Double,
  /** 0 = freshly formed and rough, 1 = ancient craton, eroded flat and hard. */
  val age: Double
) {
  val isOceanic get() = type == PlateType.OCEANIC

  /** The plate type as a number, so it can be cross-faded across a boundary rather than stepping. */
  val oceanicity get() = if (isOceanic) 1.0 else 0.0
}

/** The classification of one plate pair, computed once and shared by every cell along the boundary. */
data class BoundaryContact(
  val a: Int,
  val b: Int,
  val type: BoundaryType,
  /**
   * Rate at which the two are closing along the boundary normal. Negative when they are separating,
   * near zero for a transform boundary. Drives orogeny amplitude, so a barely-converging pair makes
   * hills and a fast one makes the Himalaya.
   */
  val convergence: Double
) {
  /** Amplitude scale in `[0,1]`: how vigorous this boundary is. */
  val strength: Double get() = min(1.0, abs(convergence) / REFERENCE_RATE)

  companion object {
    /** Convergence rate treated as "as fast as plates get". */
    const val REFERENCE_RATE = 0.9

    /** Below this the boundary-normal motion is noise and the boundary is a transform. */
    const val TRANSFORM_THRESHOLD = 0.12
  }
}

/**
 * The plate layout of one world: seeds, their properties, and a spatial index over them.
 *
 * Cell-to-plate assignment is a Voronoi partition of a *domain-warped* position rather than of the
 * position itself. Without the warp the boundaries are straight bisectors and the coastlines they
 * produce are visibly polygonal - which is the single most recognisable tell of a Voronoi-based
 * generator.
 */
class PlateSet(
  val plates: List<Plate>,
  private val bounds: Aabb,
  private val warpSeed: Long,
  private val warpAmplitude: Double,
  private val warpFrequency: Double
) {

  private val index = PointIndex(plates.map { it.seed }, bounds.expanded(warpAmplitude * 2.0))

  /**
   * Classification per unordered plate pair, indexed by `lo * plates.size + hi`.
   *
   * A dense table filled on demand rather than a memo map behind a lock. It was the map, and the lock was
   * harmless while the tectonic loop ran on one thread - but that loop is a per-cell pure function over a
   * quarter of a million cells and it calls this once per cell, so as soon as the rows are split the memo
   * becomes the one piece of contention in the hottest loop of the stage.
   *
   * A plain array of nulls needs no lock at all, and not because of anything subtle about the memory
   * model: [classify] is a pure function of two plates, so two threads racing on the same slot compute the
   * same value and it does not matter which one lands. A stale null costs a recomputation, never a wrong
   * answer. Plate counts are dozens, so the table is a few thousand references at worst.
   */
  private val contacts = arrayOfNulls<BoundaryContact>(plates.size * plates.size)

  val size get() = plates.size

  /**
   * Which plate owns `(x, y)`, how far the cell is from that plate's boundary, and which plate is on
   * the far side of it.
   *
   * @param out receives `[plateId, otherPlateId, boundaryDistanceMetres]`
   */
  fun sampleInto(x: Double, y: Double, out: DoubleArray, scratch: DoubleArray) {
    val warped = Noise.warp(warpSeed, x, y, warpAmplitude, warpFrequency, WARP_OCTAVES)

    index.nearestTwo(warped[0], warped[1], scratch)

    val nearest = scratch[0].toInt()
    val other = scratch[1].toInt()

    out[0] = nearest.toDouble()
    out[1] = other.toDouble()
    // Distance to the perpendicular bisector of the two seeds, which is where the Voronoi boundary
    // runs. Exact for the bisector, and near enough for the warped one at the scales that matter.
    out[2] = if (other < 0) Double.MAX_VALUE else (scratch[3] - scratch[2]) * 0.5
  }

  /** The classification of the boundary between two plates. Memoised; safe to call from any thread. */
  fun contact(a: Int, b: Int): BoundaryContact {
    val lo = min(a, b)
    val hi = if (lo == a) b else a
    val slot = lo * plates.size + hi

    contacts[slot]?.let { return it }

    val classified = classify(plates[lo], plates[hi])
    contacts[slot] = classified
    return classified
  }

  private fun classify(a: Plate, b: Plate): BoundaryContact {
    val normal = (b.seed - a.seed).normalized()
    // Positive when a moves toward b and b moves toward a.
    val convergence = (a.drift dot normal) - (b.drift dot normal)

    val type = when {
      convergence > BoundaryContact.TRANSFORM_THRESHOLD -> BoundaryType.CONVERGENT
      convergence < -BoundaryContact.TRANSFORM_THRESHOLD -> BoundaryType.DIVERGENT
      else -> BoundaryType.TRANSFORM
    }

    return BoundaryContact(a.id, b.id, type, convergence)
  }

  companion object {

    private const val WARP_OCTAVES = 2

    /**
     * Poisson-disk plate seeds with per-plate type, drift, base elevation and age.
     *
     * @param spacing target centre-to-centre plate spacing in metres
     * @param oceanicShare fraction of plates that are oceanic. Earth is about 0.6 by count; the land
     *   fraction that results is then normalised by the tectonics stage, because the count says
     *   surprisingly little about the area.
     */
    fun build(
      bounds: Aabb,
      spacing: Double,
      rng: GenRng,
      oceanicShare: Double = 0.6
    ): PlateSet {
      val seeds = PoissonDisk.sample(bounds, spacing, rng)

      val plates = ArrayList<Plate>(seeds.size)
      for (i in seeds.indices) {
        val oceanic = rng.nextDouble() < oceanicShare
        val angle = rng.nextDouble() * 2.0 * Math.PI
        // Speeds vary by a factor of a few, which is what makes some boundaries dramatic and others
        // barely register. All the same magnitude would give every range the same height.
        val speed = 0.25 + rng.nextDouble() * 0.75
        val age = rng.nextDouble().pow(0.7)

        plates.add(
          Plate(
            id = i,
            seed = seeds[i],
            type = if (oceanic) PlateType.OCEANIC else PlateType.CONTINENTAL,
            drift = Vec2d(cos(angle), sin(angle)) * speed,
            // Deliberately a narrow spread for continental plates. Plate identity should show in the
            // *mountains* - which come from the boundaries - not in the average height of a plate's
            // interior. A wide spread here makes each plate a visibly distinct block of colour on the
            // map however smoothly the boundaries are blended, because the eye reads the interiors.
            // Regional variation in height is the regional swell's job; see TectonicsParams.
            baseElevation = if (oceanic) {
              -3000.0 - rng.nextDouble() * 900.0
            } else {
              // Old cratons sit lower: they have had longer to be worn down.
              260.0 + rng.nextDouble() * 200.0 - age * 140.0
            },
            age = age
          )
        )
      }

      return PlateSet(
        plates = plates,
        bounds = bounds,
        warpSeed = GenRng.mix64(rng.nextLong()),
        warpAmplitude = spacing * WARP_FRACTION,
        warpFrequency = 1.0 / (spacing * WARP_WAVELENGTH_FRACTION)
      )
    }

    /**
     * How far the warp can push a boundary, as a fraction of plate spacing.
     *
     * Has to be a substantial fraction, not a token one. At a tenth of the spacing the boundaries are
     * still recognisably the straight bisectors of a Voronoi diagram with a wobble on them; at a third
     * the polygon is gone and what is left reads as a coastline.
     */
    private const val WARP_FRACTION = 0.32

    /** Warp wavelength as a fraction of plate spacing: long enough to bend a boundary, not fray it. */
    private const val WARP_WAVELENGTH_FRACTION = 0.9
  }
}

/**
 * Boundary stress turned into landforms.
 *
 * Every profile here is a falloff away from the boundary, and the *shape* of the falloff is what
 * distinguishes the landforms: fold mountains are broad and symmetric, volcanic arcs peak inland of
 * the boundary rather than on it, trenches are narrow and deep, rifts are a narrow trough between two
 * raised shoulders. Getting these shapes right is worth more to how a world reads than any amount of
 * noise tuning, because it is what makes a mountain range have a near side and a far side.
 */
object Orogeny {

  /**
   * Elevation contribution at a cell, in metres.
   *
   * @param distance metres from the boundary
   * @param own the plate this cell belongs to
   * @param other the plate on the far side
   */
  fun elevationAt(
    contact: BoundaryContact,
    own: Plate,
    other: Plate,
    distance: Double
  ): Double {
    val strength = contact.strength

    return when (contact.type) {
      BoundaryType.CONVERGENT -> when {
        !own.isOceanic && !other.isOceanic ->
          // Continent-continent: fold mountains. Broad, symmetric, and the tallest thing there is.
          3400.0 * strength * falloff(distance, 190_000.0, 1.5)

        own.isOceanic && !other.isOceanic ->
          // The subducting side: a trench right at the boundary, and nothing else.
          -4600.0 * strength * falloff(distance, 42_000.0, 2.0)

        !own.isOceanic && other.isOceanic ->
          // The overriding side: a volcanic arc, peaking well inland of the trench. That offset is
          // the diagnostic trait of an active margin - the Andes are not on the coastline.
          2400.0 * strength * ridge(distance, 70_000.0, 62_000.0) +
              700.0 * strength * falloff(distance, 240_000.0, 1.2)

        else -> {
          // Ocean-ocean: island arc on the older plate, trench on the younger. Breaking the tie by
          // age rather than by id keeps it geologically motivated and still deterministic.
          val overriding = own.age >= other.age
          if (overriding) {
            1900.0 * strength * ridge(distance, 45_000.0, 40_000.0)
          } else {
            -5000.0 * strength * falloff(distance, 36_000.0, 2.0)
          }
        }
      }

      BoundaryType.DIVERGENT -> when {
        own.isOceanic ->
          // Mid-ocean ridge: a broad swell, because young lithosphere is hot and buoyant.
          1500.0 * strength * falloff(distance, 320_000.0, 1.3)

        else ->
          // Continental rift: a narrow graben between two uplifted shoulders.
          -1100.0 * strength * falloff(distance, 26_000.0, 2.0) +
              750.0 * strength * ridge(distance, 52_000.0, 34_000.0)
      }

      BoundaryType.TRANSFORM ->
        // Transform boundaries build little, but they are not invisible: a low ridge with a linear valley
        // along it, which is what gives a strike-slip fault its recognisable trace.
        //
        // Kept shallow and wide on purpose. A deep narrow notch here is only a handful of kilometre cells
        // across, so it comes out as a uniform-width gash running the full length of every transform
        // boundary in the world - which reads as a line somebody drew rather than as a valley.
        220.0 * ridge(distance, 26_000.0, 18_000.0) -
            110.0 * falloff(distance, 17_000.0, 1.6)
    }
  }

  /**
   * Uplift rate for the stream power law, in metres per erosion timestep.
   *
   * Only convergent boundaries and continental interiors uplift. Without the interior baseline an
   * old shield erodes to a featureless plain over the erosion run, which is not what shields look
   * like - they are low, but they still have relief.
   */
  fun upliftAt(
    contact: BoundaryContact,
    own: Plate,
    other: Plate,
    distance: Double
  ): Double {
    if (own.isOceanic) return 0.0

    // Calibrated against the erosion stage rather than against geology. At steady state the stream
    // power law gives slope = U / (K A^m), so what these numbers really set is the equilibrium
    // steepness of the terrain: about 0.02 in a quiet interior and about 0.15 on an active orogen's
    // headwaters. Picking them from the target slope is the only way to make them mean anything - as
    // metres per "timestep" they are otherwise unfalsifiable. See ErosionParams.erodibility.
    val interior = INTERIOR_UPLIFT * (1.0 - own.age * 0.6)

    val tectonic = when (contact.type) {
      BoundaryType.CONVERGENT -> {
        val orogenic = elevationAt(contact, own, other, distance)
        if (orogenic > 0.0) orogenic * OROGENIC_UPLIFT_PER_METRE else 0.0
      }
      // Rift shoulders are actively rising even though the rift floor is dropping.
      BoundaryType.DIVERGENT -> 4.3 * contact.strength * ridge(distance, 52_000.0, 34_000.0)
      BoundaryType.TRANSFORM -> 0.7 * ridge(distance, 22_000.0, 16_000.0)
    }

    return interior + tectonic
  }

  /**
   * How much this cell is caught up in mountain building, in `[0,1]`.
   *
   * Used to modulate how rough the noise is: orogens are craggy, plate interiors are not, and using
   * one noise amplitude everywhere is what makes a fractal world look uniform.
   */
  fun orogenicIntensity(contact: BoundaryContact, distance: Double): Double {
    val width = when (contact.type) {
      BoundaryType.CONVERGENT -> 200_000.0
      BoundaryType.DIVERGENT -> 90_000.0
      BoundaryType.TRANSFORM -> 40_000.0
    }
    return (contact.strength * falloff(distance, width, 1.3)).coerceIn(0.0, 1.0)
  }

  /**
   * Uplift in a quiet continental interior. Holds a shield's gentle relief against erosion.
   *
   * This and [net.bestia.worldgen.geo.ErosionParams.erodibility] are one number, not two: the finished
   * relief depends only on their ratio, because at steady state `S = U / (K A^m)`. Around 1.2 against an
   * erodibility of 0.115 gives a quiet interior an equilibrium slope near 0.02 - twenty metres per
   * kilometre, a gentle plain - while medium-hard rock holds two to four times that. Change one of them
   * and the other has to move with it or the world flattens.
   */
  const val INTERIOR_UPLIFT = 1.2

  /** Uplift per metre of orogenic elevation, so a taller range is also a more active one. */
  private const val OROGENIC_UPLIFT_PER_METRE = 0.00265

  /** 1 at the boundary, easing to 0 over [width]. [shape] above 1 keeps more of the crest flat. */
  private fun falloff(distance: Double, width: Double, shape: Double): Double {
    val t = (distance / width).coerceAtLeast(0.0)
    return exp(-t.pow(shape))
  }

  /** A crest at [peak] metres from the boundary, [width] wide - a volcanic arc or a rift shoulder. */
  private fun ridge(distance: Double, peak: Double, width: Double): Double {
    val t = (distance - peak) / width
    return exp(-t * t)
  }
}
