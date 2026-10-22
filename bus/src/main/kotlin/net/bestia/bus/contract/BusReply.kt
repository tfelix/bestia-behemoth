package net.bestia.bus.contract

/**
 * A zone's reply to one [BusRequest]. [answer] is set when [status] is [Status.OK], and on
 * [Status.NOT_MODIFIED] for what the caller needs to answer conditionally (an ETag and how to cache it).
 * [body] carries binary content that has no place in JSON, a map tile's or a guild emblem's PNG.
 */
class BusReply(
  val correlationId: String,
  val status: Status,
  val answer: ZoneAnswer? = null,
  val detail: String? = null,
  val body: ByteArray = EMPTY,
) {

  enum class Status {
    OK,

    /** The `ifNoneMatch` of the query still matches; nothing to send. */
    NOT_MODIFIED,

    /** Absent, or not the asker's. The two are deliberately indistinguishable. */
    NOT_FOUND,

    /** Malformed: a level outside the pyramid, an unknown stance. */
    INVALID,

    /** The zone is at its limit; ask again shortly. */
    BUSY,
  }

  companion object {
    val EMPTY = ByteArray(0)

    fun ok(correlationId: String, answer: ZoneAnswer, body: ByteArray = EMPTY): BusReply {
      return BusReply(correlationId, Status.OK, answer, body = body)
    }

    fun of(correlationId: String, status: Status, detail: String? = null): BusReply {
      return BusReply(correlationId, status, detail = detail)
    }
  }
}
