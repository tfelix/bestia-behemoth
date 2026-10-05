package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto.Envelope
import kotlin.reflect.KClass

/** Reads one [Envelope] case into the message its handler takes. */
class WireDecoder<T : CMSG>(
  val case: Envelope.MessageCase,
  val type: KClass<T>,
  private val parse: (accountId: Long, envelope: Envelope) -> T?,
) {
  /**
   * Null when the payload is well-formed protobuf but not a message this server accepts, e.g. an equip slot
   * this version does not know: a misbehaving client, so the message is dropped rather than the connection.
   */
  fun decode(accountId: Long, envelope: Envelope): T? {
    return parse(accountId, envelope)
  }
}

inline fun <reified T : CMSG> decoder(
  case: Envelope.MessageCase,
  noinline parse: (accountId: Long, envelope: Envelope) -> T?,
): WireDecoder<T> {
  return WireDecoder(case, T::class, parse)
}
