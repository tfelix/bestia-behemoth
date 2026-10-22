package net.bestia.zone.cartography.habitat

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.IntLayer
import net.bestia.zone.cartography.tile.TileId
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.SplittableRandom
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where one species lives, painted over a map tile: a light tint over the biomes it spawns in, and a warm haze
 * around its dens. Transparent everywhere else, so it lies on top of the map tile.
 *
 * The haze narrows a search without marking a den: each blob is centred up to [JITTER_METRES] away from its den,
 * by an offset fixed per den, so asking again cannot average the offset away. See [hazeCentre].
 */
class HabitatLayer(
  private val biome: IntLayer,
  /** Empty means any land, as for the wild spawner. */
  private val habitat: Set<Biome>,
  private val hazeCentres: List<HazeCentre>,
) {

  data class HazeCentre(val x: Double, val y: Double)

  fun render(tile: TileId): BufferedImage {
    val view = tile.viewport()
    val image = BufferedImage(TileId.TILE_PIXELS, TileId.TILE_PIXELS, BufferedImage.TYPE_INT_ARGB)
    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    val reach = HAZE_SIGMA_METRES * HAZE_REACH_SIGMAS
    val near = hazeCentres.filter { tile.bounds.expanded(reach).contains(it.x, it.y) }

    for (py in 0 until TileId.TILE_PIXELS) {
      val worldY = view.worldY(py)
      for (px in 0 until TileId.TILE_PIXELS) {
        val worldX = view.worldX(px)
        val tint = share(worldX, worldY, ::isHabitat)
        // Kept off the water: a blob near the shore would otherwise send a player swimming.
        val heat = if (near.isEmpty()) 0.0 else heatAt(worldX, worldY, near) * share(worldX, worldY, ::isLand)

        val alpha = max(tint * TINT_ALPHA, heat * HEAT_ALPHA).roundToInt()
        pixels[py * TileId.TILE_PIXELS + px] = if (alpha == 0) 0 else (alpha shl 24) or blend(TINT_RGB, HEAT_RGB, heat)
      }
    }

    return image
  }

  /** How much of the ground around a point is [wanted], 0 to 1, blended over the four nearest biome cells. */
  private fun share(worldX: Double, worldY: Double, wanted: (Biome) -> Boolean): Double {
    val metresPerCell = biome.region.resolution.metresPerCell
    val fx = worldX / metresPerCell - 0.5
    val fy = worldY / metresPerCell - 0.5
    val x0 = floor(fx).toInt()
    val y0 = floor(fy).toInt()
    val tx = fx - x0
    val ty = fy - y0

    var inside = 0.0
    for (dy in 0..1) {
      for (dx in 0..1) {
        val x = x0 + dx
        val y = y0 + dy
        if (!biome.region.contains(x, y)) continue
        val cover = Biome.entries.getOrNull(biome[x, y]) ?: continue
        if (!wanted(cover)) continue

        inside += (if (dx == 0) 1.0 - tx else tx) * (if (dy == 0) 1.0 - ty else ty)
      }
    }
    return inside
  }

  private fun isHabitat(cover: Biome): Boolean {
    return isLand(cover) && (habitat.isEmpty() || cover in habitat)
  }

  private fun isLand(cover: Biome): Boolean {
    return cover != Biome.OCEAN && cover != Biome.LAKE
  }

  private fun heatAt(worldX: Double, worldY: Double, near: List<HazeCentre>): Double {
    var heat = 0.0
    for (centre in near) {
      val dx = worldX - centre.x
      val dy = worldY - centre.y
      heat += exp(-(dx * dx + dy * dy) / (2 * HAZE_SIGMA_METRES * HAZE_SIGMA_METRES))
    }
    // Saturates softly, so a cluster of dens reads as a warmer area rather than as one flat colour.
    return 1.0 - exp(-heat * HEAT_GAIN)
  }

  private fun blend(from: Int, to: Int, t: Double): Int {
    fun channel(shift: Int): Int {
      val a = (from ushr shift) and 0xFF
      val b = (to ushr shift) and 0xFF
      return (a + (b - a) * t).roundToInt() shl shift
    }
    return channel(16) or channel(8) or channel(0)
  }

  companion object {
    /** Bumped when the look changes, so clients drop tiles drawn the old way. */
    const val REVISION = 1

    /** A blob is about [HAZE_SIGMA_METRES] * 4 across where it is still visible. */
    const val HAZE_SIGMA_METRES = 200.0
    const val JITTER_METRES = 250.0

    /** Never centred on the den: the offset is at least this share of [JITTER_METRES]. */
    private const val MIN_JITTER_SHARE = 0.4
    private const val HAZE_REACH_SIGMAS = 3.0
    private const val HEAT_GAIN = 1.2
    private const val TINT_ALPHA = 70.0
    private const val HEAT_ALPHA = 150.0
    private const val TINT_RGB = 0xE6B04A
    private const val HEAT_RGB = 0xD8452A

    /**
     * Where the haze around one den is centred. Fixed by the world seed, the den and the species, so every request
     * draws the same blob; drawn at random, the den would be the average of a few requests.
     */
    fun hazeCentre(worldSeed: Long, denFeatureId: Long, speciesId: Long, denX: Double, denY: Double): HazeCentre {
      val random = SplittableRandom(worldSeed xor (denFeatureId * DEN_MIX) xor (speciesId * SPECIES_MIX))
      val angle = random.nextDouble(2 * Math.PI)
      val distance = JITTER_METRES * (MIN_JITTER_SHARE + (1 - MIN_JITTER_SHARE) * random.nextDouble())

      return HazeCentre(denX + cos(angle) * distance, denY + sin(angle) * distance)
    }

    private const val DEN_MIX = -0x61c8864680b583ebL
    private const val SPECIES_MIX = 0x632be59bd9b4e019L
  }
}
