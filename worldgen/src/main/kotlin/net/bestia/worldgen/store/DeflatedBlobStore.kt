package net.bestia.worldgen.store

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Wraps another [ChunkBlobStore] and deflates what goes into it.
 *
 * Run-length encoding removes the long vertical runs; general-purpose compression then removes the *repetition
 * between* runs, which RLE cannot see. A surface chunk measures at about 14.7 kB encoded and 3.1 kB deflated,
 * so this is another factor of four and a half on top of the thirty-six that RLE already gives - together
 * about a hundred and seventy times smaller than the raw voxels.
 *
 * ### Why a decorator rather than part of the codec
 *
 * Three reasons, and the first is the one that matters. The codec's output is also the wire format, and the
 * right compression for a disk is not necessarily the right compression for a socket - the client may be behind
 * a transport that already compresses, or want a cheaper setting for latency. Second, compression choice must
 * not consume chunk format versions: changing it has to be invisible to [PipelineVersion], because it changes
 * nothing about what the chunk *is*. Third, it composes with tiering - a hot in-memory tier wants no
 * compression at all, and a cold object store wants the most it can afford.
 *
 * ### Small payloads
 *
 * Deflate has a fixed overhead and loses on very small inputs: a uniform underground chunk encodes to thirteen
 * bytes and deflates to nineteen. There are a very great many of those chunks, so the store keeps whichever is
 * smaller and records which it chose in a leading byte. Compressing unconditionally would make the most
 * common chunk in the world bigger.
 */
class DeflatedBlobStore(
  private val delegate: ChunkBlobStore,
  private val level: Int = Deflater.BEST_COMPRESSION,
  /** Below this many bytes, compression is not attempted at all. */
  private val minimumBytes: Int = 64
) : ChunkBlobStore {

  override fun get(key: Long): ByteArray? {
    val stored = delegate.get(key) ?: return null
    require(stored.isNotEmpty()) { "Blob $key is empty; it cannot even say whether it is compressed" }

    return when (stored[0]) {
      STORED -> stored.copyOfRange(1, stored.size)
      DEFLATED -> inflate(key, stored)
      else -> throw IllegalStateException(
        "Blob $key has framing byte ${stored[0]}, which is neither stored nor deflated"
      )
    }
  }

  override fun put(key: Long, blob: ByteArray) {
    val deflated = if (blob.size >= minimumBytes) deflate(blob) else null

    if (deflated != null && deflated.size + 1 < blob.size + 1) {
      delegate.put(key, framed(DEFLATED, deflated))
    } else {
      delegate.put(key, framed(STORED, blob))
    }
  }

  override fun remove(key: Long) = delegate.remove(key)

  private fun deflate(blob: ByteArray): ByteArray {
    val deflater = Deflater(level)
    try {
      deflater.setInput(blob)
      deflater.finish()

      val out = ByteArrayOutputStream(blob.size / 2 + 32)
      val buffer = ByteArray(8192)
      while (!deflater.finished()) {
        val n = deflater.deflate(buffer)
        if (n == 0) break
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    } finally {
      deflater.end()
    }
  }

  private fun inflate(key: Long, stored: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
      inflater.setInput(stored, 1, stored.size - 1)

      val out = ByteArrayOutputStream(stored.size * 4)
      val buffer = ByteArray(8192)
      while (!inflater.finished()) {
        val n = inflater.inflate(buffer)
        // A truncated or corrupt payload stops producing output without ever reporting finished. Saying so is
        // the whole point of a store that might be holding a blob written months ago by another build.
        if (n == 0) {
          check(inflater.finished()) { "Blob $key is truncated or corrupt: inflate stalled" }
          break
        }
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    } finally {
      inflater.end()
    }
  }

  private fun framed(kind: Byte, payload: ByteArray): ByteArray {
    val framed = ByteArray(payload.size + 1)
    framed[0] = kind
    payload.copyInto(framed, 1)
    return framed
  }

  private companion object {
    const val STORED: Byte = 0
    const val DEFLATED: Byte = 1
  }
}
