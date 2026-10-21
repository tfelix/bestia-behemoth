package net.bestia.bus

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import java.nio.ByteBuffer

/**
 * The bytes of one bus message: a 4-byte big-endian length, that many bytes of JSON, then the binary body.
 * The body is kept out of the JSON so a tile does not grow by a third in base64.
 */
object BusFrame {

  private data class ReplyHeader(
    val correlationId: String,
    val status: BusReply.Status,
    val answer: ZoneAnswer?,
    val detail: String?,
  )

  fun encode(request: BusRequest): ByteArray {
    return frame(BusJson.mapper.writeValueAsBytes(request), BusReply.EMPTY)
  }

  fun decodeRequest(bytes: ByteArray): BusRequest {
    val (header, _) = split(bytes)
    return BusJson.mapper.readValue(header, BusRequest::class.java)
  }

  fun encode(reply: BusReply): ByteArray {
    val header = ReplyHeader(reply.correlationId, reply.status, reply.answer, reply.detail)
    return frame(BusJson.mapper.writeValueAsBytes(header), reply.body)
  }

  fun decodeReply(bytes: ByteArray): BusReply {
    val (headerBytes, body) = split(bytes)
    val header = BusJson.mapper.readValue(headerBytes, ReplyHeader::class.java)
    return BusReply(header.correlationId, header.status, header.answer, header.detail, body)
  }

  private fun frame(header: ByteArray, body: ByteArray): ByteArray {
    return ByteBuffer.allocate(Int.SIZE_BYTES + header.size + body.size)
      .putInt(header.size)
      .put(header)
      .put(body)
      .array()
  }

  private fun split(bytes: ByteArray): Pair<ByteArray, ByteArray> {
    val buffer = ByteBuffer.wrap(bytes)
    val headerLength = buffer.getInt()
    require(headerLength in 0..buffer.remaining()) { "Frame header of $headerLength bytes in ${bytes.size}" }

    val header = ByteArray(headerLength).also { buffer.get(it) }
    val body = ByteArray(buffer.remaining()).also { buffer.get(it) }
    return header to body
  }
}
