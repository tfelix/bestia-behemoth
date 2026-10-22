package net.bestia.zone.guild

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * The rule for a guild emblem: a [SIZE]x[SIZE] PNG of at most [MAX_BYTES]. Small enough to stay crisp only at
 * whole-number scales, which is how every client draws it.
 *
 * What is stored is never the upload itself but the decoded pixels written out again as a fresh PNG, so nothing
 * an uploader hid in the file - metadata, extra chunks, data after its end - reaches other players.
 */
object EmblemImage {

  const val SIZE = 32
  const val MAX_BYTES = 8 * 1024

  private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

  /** [hash] is the hex SHA-256 of [png], so a new emblem is a new name and a cached one never goes stale. */
  class Clean(val png: ByteArray, val hash: String)

  /** Null when [upload] is too big, not a PNG, or not exactly [SIZE] pixels square. */
  fun cleanOrNull(upload: ByteArray): Clean? {
    if (upload.size > MAX_BYTES || !upload.startsWith(PNG_SIGNATURE)) {
      return null
    }

    val pixels = decodeIfExactSize(upload) ?: return null
    val png = encodeFresh(pixels)

    return Clean(png, sha256Hex(png))
  }

  /** Reads the header before any pixel, so a small file claiming a huge image is refused undecoded. */
  private fun decodeIfExactSize(upload: ByteArray): BufferedImage? {
    val reader = ImageIO.getImageReadersByFormatName("png").next()

    return try {
      ImageIO.createImageInputStream(ByteArrayInputStream(upload)).use { input ->
        reader.input = input
        if (reader.getWidth(0) != SIZE || reader.getHeight(0) != SIZE) null else reader.read(0)
      }
    } catch (_: IOException) {
      null
    } finally {
      reader.dispose()
    }
  }

  private fun encodeFresh(pixels: BufferedImage): ByteArray {
    val fresh = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
    val graphics = fresh.createGraphics()
    try {
      graphics.drawImage(pixels, 0, 0, null)
    } finally {
      graphics.dispose()
    }

    return ByteArrayOutputStream().use { out ->
      ImageIO.write(fresh, "png", out)
      out.toByteArray()
    }
  }

  private fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  }

  private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    return size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
  }
}
