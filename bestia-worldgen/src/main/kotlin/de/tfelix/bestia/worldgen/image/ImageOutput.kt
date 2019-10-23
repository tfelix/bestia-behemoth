package de.tfelix.bestia.worldgen.image

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.Map2DDiscreteChunk
import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.workload.Job
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

fun toRGB(r: Int, g: Int, b: Int): Int {
  return r shl 16 or (g shl 8 and 0xFF0000) or (b and 0xFFFF00)
}

class ImageOutputJob(
    private val bitmapFile: Path,
    width: Int,
    height: Int
) : Job() {

  private val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

  override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate) {
    vec.getValueDouble("noise")
    val part2D = data.mapChunk as Map2DDiscreteChunk
    val global = part2D.toGlobalCoordinates(cord) as Map2DDiscreteCoordinate

    image.setRGB(global.x.toInt(), global.y.toInt(), grayScale(vec.getValueDouble("chunkHeight")))
  }

  private fun grayScale(scale: Double): Int {
    val rgb = Math.min(0.0, Math.max(255.0,255 * scale)).toInt()
    return toRGB(rgb, rgb, rgb)
  }

  override fun onFinish(dao: MapGenDAO, data: MapDataPart) {
    ImageIO.write(image, "png", bitmapFile.toFile())
  }
}