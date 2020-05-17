package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

open class ImageOutputJob(
    private val bitmapFile: File
) : ChunkJob {

  override val name = "Image output"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    val image = BufferedImage(
        noiseMap.size.width.toInt(),
        noiseMap.size.height.toInt(),
        BufferedImage.TYPE_INT_RGB
    )
    val graphics = image.graphics
    graphics.color = Color.BLACK
    graphics.fillRect(0, 0, image.width, image.height)

    noiseMap.forEach {
      image.setRGB(it.first.x, it.first.y, getColor(it.second))
    }

    ImageIO.write(image, "png", bitmapFile)

    return noiseMap
  }

  protected open fun getColor(value: Double): Int {
    return grayScale(value)
  }

  protected fun grayScale(scale: Double): Int {
    val rgb = max(0.0, min(255.0, 255 * scale)).toInt()
    return toRGB(rgb, rgb, rgb)
  }

  protected fun toRGB(r: Int, g: Int, b: Int): Int {
    val rs = r shl 16
    val gr = (g shl 8)
    return rs or gr or b
  }
}