import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Writes the terrain slot textures for the two road surfaces: `9_trodden` (packed earth) and `11_cobblestone`.
 *
 * ### Why generated rather than drawn
 *
 * Both slots rendered as `TerrainSlotTextures.Generate`'s grey value noise, which is honest about being a
 * placeholder and does not look like a road. Nobody has authored art for them, and a cobbled surface is mostly
 * geometry - stones, joints, a dome on each stone - which is exactly what a small deterministic generator can
 * draw and what a tint over another slot's grain cannot. The output is committed, so the client never runs this;
 * authored art dropped in under the same names simply replaces it.
 *
 * ### What the client does with them
 *
 * `TerrainSlotTextures` reads `_albedo`, `_height`, `_normal`, `_ao` and `_roughness` per slot, all at 512 and
 * tiling every two metres (`slot_uv_scale`). The albedo's **mean colour is divided out** and the block's palette
 * tint applied instead, so these are kept close to neutral: what they carry is structure, and the colour is the
 * palette's. The height drives the shader's height blend, which is what lets grass and earth settle into the
 * joints between cobbles at a road's edge instead of meeting it along a straight fade.
 *
 * Every map tiles: the cobbles are a Voronoi diagram over a jittered lattice measured with wrap-around distance,
 * and every noise is periodic in the tile.
 *
 * Normals follow the terrain shader's own convention (`terrain_common.gdshaderinc`, the top projection): R is
 * minus the height's slope along the image's columns and G along its rows, which is world X and world Z.
 */
abstract class RoadTextureTask : DefaultTask() {

  /** `bestia-client/src/Game/World/Shader/Slots`. */
  @get:OutputDirectory
  abstract val slotDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val out = slotDirectory.get().asFile
    write(out, "9_trodden", trodden())
    write(out, "11_cobblestone", cobblestone())
  }

  /** One slot's maps, before they are written. Heights, occlusion and roughness in `[0, 1]`. */
  private class Maps(
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val height: FloatArray,
    val occlusion: FloatArray,
    val roughness: FloatArray
  )

  // --- Packed earth -------------------------------------------------------------------------------

  /**
   * Earth beaten hard: a fine grit, a broad mottle where it is more or less compacted, and small stones pressed
   * into it. Flatter than loose soil - that is the difference between a path and a field - so the relief is
   * mostly the pebbles.
   */
  private fun trodden(): Maps {
    val n = SIZE * SIZE
    val height = FloatArray(n)
    val tone = FloatArray(n)

    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        val mottle = fbm(x, y, 4, 3, 11)
        val grit = fbm(x, y, 64, 2, 23)
        height[y * SIZE + x] = 0.35f + 0.10f * mottle + 0.05f * grit
        tone[y * SIZE + x] = 0.92f + 0.12f * (mottle - 0.5f) + 0.10f * (grit - 0.5f)
      }
    }

    // Pebbles: small domes, a little paler or darker than the earth round them.
    val rng = Hash(0x9e3779b9L)
    repeat(PEBBLES) { i ->
      val cx = rng.unit(i, 0) * SIZE
      val cy = rng.unit(i, 1) * SIZE
      val radius = 2.0f + rng.unit(i, 2) * 5.0f
      val shade = 0.80f + rng.unit(i, 3) * 0.25f
      val reach = radius.toInt() + 1
      for (dy in -reach..reach) {
        for (dx in -reach..reach) {
          val d = sqrt((dx * dx + dy * dy).toFloat()) / radius
          if (d >= 1.0f) continue
          val at = wrap(cy.toInt() + dy) * SIZE + wrap(cx.toInt() + dx)
          val dome = sqrt(1.0f - d * d)
          height[at] = max(height[at], 0.45f + 0.35f * dome)
          tone[at] = tone[at] * (1.0f - dome) + shade * dome
        }
      }
    }

    val occlusion = occlusionOf(height, 0.35f)
    return Maps(
      red = FloatArray(n) { tone[it] * 0.58f * occlusion[it] },
      green = FloatArray(n) { tone[it] * 0.50f * occlusion[it] },
      blue = FloatArray(n) { tone[it] * 0.42f * occlusion[it] },
      height = height,
      occlusion = occlusion,
      roughness = FloatArray(n) { 0.92f - 0.10f * (height[it] - 0.35f) }
    )
  }

  // --- Cobblestone --------------------------------------------------------------------------------

  /**
   * Rounded field stones set in earth: a Voronoi cell per stone, domed, each a slightly different stone, with
   * dark joints between them that sit well below the tops.
   *
   * Cobbles rather than squared setts, because a medieval road was paved with what the river gave up.
   */
  private fun cobblestone(): Maps {
    val n = SIZE * SIZE
    val cells = STONES_ACROSS
    val pitch = SIZE.toFloat() / cells
    val rng = Hash(0x51ed270bL)

    // One jittered site per lattice cell, and per stone a tone, a warmth and how proud of the road it stands.
    val siteX = FloatArray(cells * cells)
    val siteY = FloatArray(cells * cells)
    val stoneTone = FloatArray(cells * cells)
    val stoneWarmth = FloatArray(cells * cells)
    val stoneLift = FloatArray(cells * cells)
    for (j in 0 until cells) {
      for (i in 0 until cells) {
        val k = j * cells + i
        siteX[k] = (i + 0.15f + 0.7f * rng.unit(k, 0)) * pitch
        siteY[k] = (j + 0.15f + 0.7f * rng.unit(k, 1)) * pitch
        stoneTone[k] = 0.78f + 0.30f * rng.unit(k, 2)
        stoneWarmth[k] = (rng.unit(k, 3) - 0.5f) * 0.05f
        stoneLift[k] = 0.85f + 0.15f * rng.unit(k, 4)
      }
    }

    val height = FloatArray(n)
    val red = FloatArray(n)
    val green = FloatArray(n)
    val blue = FloatArray(n)
    val joint = FloatArray(n)

    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        var first = Float.MAX_VALUE
        var second = Float.MAX_VALUE
        var nearest = 0
        val ci = floor(x / pitch).toInt()
        val cj = floor(y / pitch).toInt()

        for (dj in -2..2) {
          for (di in -2..2) {
            val wi = Math.floorMod(ci + di, cells)
            val wj = Math.floorMod(cj + dj, cells)
            val k = wj * cells + wi
            // The site's position on the copy of the tile next to this pixel, so distance wraps.
            val sx = siteX[k] + (ci + di - wi) * pitch
            val sy = siteY[k] + (cj + dj - wj) * pitch
            val d = sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy))
            if (d < first) {
              second = first
              first = d
              nearest = k
            } else if (d < second) {
              second = d
            }
          }
        }

        // Half the gap between the two nearest sites is how far inside its stone this pixel is; capping it by the
        // distance from the stone's own centre rounds off the corners a Voronoi cell would otherwise have.
        val inside = min((second - first) * 0.5f, pitch * ROUNDING - first)

        val at = y * SIZE + x
        val grit = fbm(x, y, 48, 2, 37)
        val stoneDetail = fbm(x, y, 24, 3, 41 + nearest)

        if (inside < JOINT_HALF_WIDTH) {
          joint[at] = 1.0f
          height[at] = 0.12f + 0.10f * grit
          val earth = 0.42f + 0.10f * grit
          red[at] = earth * 1.05f
          green[at] = earth * 0.95f
          blue[at] = earth * 0.82f
        } else {
          // A pillow: rises steeply off the joint and flattens across the top.
          val t = min(1.0f, (inside - JOINT_HALF_WIDTH) / (pitch * 0.35f))
          val dome = sqrt(t * (2.0f - t))
          height[at] = (0.30f + 0.62f * dome) * stoneLift[nearest] + 0.05f * (stoneDetail - 0.5f)
          val tone = stoneTone[nearest] * (0.88f + 0.24f * stoneDetail)
          red[at] = tone * (0.60f + stoneWarmth[nearest])
          green[at] = tone * 0.59f
          blue[at] = tone * (0.57f - stoneWarmth[nearest])
        }
      }
    }

    val occlusion = occlusionOf(height, 0.55f)
    for (i in 0 until n) {
      red[i] *= occlusion[i]
      green[i] *= occlusion[i]
      blue[i] *= occlusion[i]
    }

    return Maps(
      red = red,
      green = green,
      blue = blue,
      height = height,
      occlusion = occlusion,
      // Worn stone tops have a little sheen; the earth in the joints has none.
      roughness = FloatArray(n) { if (joint[it] > 0.0f) 0.97f else 0.70f + 0.15f * (1.0f - height[it]) }
    )
  }

  // --- Shared -------------------------------------------------------------------------------------

  /**
   * A cheap cavity term: how far below the local average height a texel sits, as darkening. Not ray-traced
   * occlusion, and it does not need to be - it is what puts shadow in a joint and none on a stone top.
   */
  private fun occlusionOf(height: FloatArray, strength: Float): FloatArray {
    val blurred = boxBlur(boxBlur(height, 6), 6)
    return FloatArray(height.size) { 1.0f - strength * max(0.0f, blurred[it] - height[it]) * 2.5f }
      .also { for (i in it.indices) it[i] = it[i].coerceIn(0.3f, 1.0f) }
  }

  private fun boxBlur(source: FloatArray, radius: Int): FloatArray {
    val across = FloatArray(source.size)
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        var sum = 0.0f
        for (d in -radius..radius) sum += source[y * SIZE + wrap(x + d)]
        across[y * SIZE + x] = sum / (2 * radius + 1)
      }
    }
    val out = FloatArray(source.size)
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        var sum = 0.0f
        for (d in -radius..radius) sum += across[wrap(y + d) * SIZE + x]
        out[y * SIZE + x] = sum / (2 * radius + 1)
      }
    }
    return out
  }

  private fun write(directory: File, name: String, maps: Maps) {
    directory.mkdirs()

    val albedo = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
    val normal = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        val at = y * SIZE + x
        albedo.setRGB(x, y, rgb(maps.red[at], maps.green[at], maps.blue[at]))

        // Slope in height units per texel, scaled to the relief the surface should read as having.
        val dx = (maps.height[y * SIZE + wrap(x + 1)] - maps.height[y * SIZE + wrap(x - 1)]) * 0.5f * NORMAL_RELIEF
        val dy = (maps.height[wrap(y + 1) * SIZE + x] - maps.height[wrap(y - 1) * SIZE + x]) * 0.5f * NORMAL_RELIEF
        val length = sqrt(dx * dx + dy * dy + 1.0f)
        normal.setRGB(x, y, rgb(-dx / length * 0.5f + 0.5f, -dy / length * 0.5f + 0.5f, 1.0f / length * 0.5f + 0.5f))
      }
    }

    ImageIO.write(albedo, "png", File(directory, "${name}_albedo.png"))
    ImageIO.write(normal, "png", File(directory, "${name}_normal.png"))
    ImageIO.write(grey(maps.height), "png", File(directory, "${name}_height.png"))
    ImageIO.write(grey(maps.occlusion), "png", File(directory, "${name}_ao.png"))
    ImageIO.write(grey(maps.roughness), "png", File(directory, "${name}_roughness.png"))
  }

  private fun grey(values: FloatArray): BufferedImage {
    val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY)
    val raster = image.raster
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) raster.setSample(x, y, 0, byte(values[y * SIZE + x]))
    }
    return image
  }

  private fun rgb(r: Float, g: Float, b: Float) = (byte(r) shl 16) or (byte(g) shl 8) or byte(b)

  private fun byte(value: Float) = (value.coerceIn(0.0f, 1.0f) * 255.0f + 0.5f).toInt()

  private fun wrap(i: Int) = Math.floorMod(i, SIZE)

  /**
   * Tileable value noise: [octaves] octaves starting at [cells] lattice cells across the tile, each doubling.
   * In `[0, 1]`. Periodic because the lattice wraps at a whole number of cells per tile at every octave.
   */
  private fun fbm(x: Int, y: Int, cells: Int, octaves: Int, salt: Int): Float {
    var sum = 0.0f
    var weight = 0.5f
    var total = 0.0f
    var c = cells
    for (o in 0 until octaves) {
      sum += weight * value(x, y, c, salt * 31 + o)
      total += weight
      weight *= 0.5f
      c *= 2
    }
    return sum / total
  }

  private fun value(x: Int, y: Int, cells: Int, salt: Int): Float {
    val fx = x.toFloat() * cells / SIZE
    val fy = y.toFloat() * cells / SIZE
    val x0 = floor(fx).toInt()
    val y0 = floor(fy).toInt()
    val tx = smooth(fx - x0)
    val ty = smooth(fy - y0)
    val h = Hash(salt.toLong())
    fun at(i: Int, j: Int) = h.unit(Math.floorMod(j, cells) * cells + Math.floorMod(i, cells), 7)
    val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
    val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
    return top + (bottom - top) * ty
  }

  private fun smooth(t: Float) = t * t * (3.0f - 2.0f * t)

  /** A stateless integer hash to `[0, 1)`, so every texel is a pure function of its inputs. */
  private class Hash(private val seed: Long) {
    fun unit(index: Int, stream: Int): Float {
      var z = seed + index * -0x61c8864680b583ebL + stream * 0x632be59bd9b4e019L
      z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
      z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
      z = z xor (z ushr 31)
      return ((z ushr 40).toFloat() / (1L shl 24).toFloat())
    }
  }

  private companion object {
    /** Matches `TerrainSlotTextures.Size`. */
    const val SIZE = 512

    /** Stones across one two-metre tile: twenty-two centimetre cobbles. */
    const val STONES_ACROSS = 9

    /** Half a joint's width, in texels. About a centimetre and a half. */
    const val JOINT_HALF_WIDTH = 2.2f

    /** How far from its centre, in lattice pitches, a stone stays full: beyond it the corners round away. */
    const val ROUNDING = 0.78f

    const val PEBBLES = 260

    /** How strongly height becomes slope in the normal map: texels of relief per unit of height. */
    const val NORMAL_RELIEF = 40.0f
  }
}
