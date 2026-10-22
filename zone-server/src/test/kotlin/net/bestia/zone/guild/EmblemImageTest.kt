package net.bestia.zone.guild

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmblemImageTest {

  @Test
  fun `a 32 pixel PNG is kept as fresh pixels under the hash of what is stored`() {
    val upload = png(32, 32)

    val clean = assertNotNull(EmblemImage.cleanOrNull(upload))

    val stored = ImageIO.read(clean.png.inputStream())
    assertEquals(32 to 32, stored.width to stored.height)
    assertEquals(0xFF336699.toInt(), stored.getRGB(5, 5))
    assertEquals(64, clean.hash.length)
  }

  @Test
  fun `whatever was appended to the file is left behind`() {
    val upload = png(32, 32) + "<script>alert(1)</script>".toByteArray()

    val clean = assertNotNull(EmblemImage.cleanOrNull(upload))

    assertFalse(String(clean.png, Charsets.ISO_8859_1).contains("<script>"))
    assertContentEquals(EmblemImage.cleanOrNull(png(32, 32))?.png, clean.png)
  }

  @Test
  fun `the wrong size, too many bytes or no PNG at all is refused`() {
    assertNull(EmblemImage.cleanOrNull(png(64, 64)))
    assertNull(EmblemImage.cleanOrNull(png(32, 32) + ByteArray(EmblemImage.MAX_BYTES)))
    assertNull(EmblemImage.cleanOrNull(jpeg(32, 32)))
    assertNull(EmblemImage.cleanOrNull(ByteArray(0)))
  }

  companion object {
    fun png(width: Int, height: Int): ByteArray {
      return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB)
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
      return encode(width, height, "jpg", BufferedImage.TYPE_INT_RGB)
    }

    private fun encode(width: Int, height: Int, format: String, type: Int): ByteArray {
      val image = BufferedImage(width, height, type)
      for (x in 0 until width) {
        for (y in 0 until height) {
          image.setRGB(x, y, 0xFF336699.toInt())
        }
      }

      return ByteArrayOutputStream().use { out ->
        ImageIO.write(image, format, out)
        out.toByteArray()
      }
    }
  }
}
