package net.bestia.zone.text

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ReadableSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/** What the thing [sourceEntityId] says to the player who read it. */
data class ReadableSMSG(
  val sourceEntityId: EntityId,
  val text: TextRef,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val readable = ReadableSmsgProto.ReadableSMSG.newBuilder()
      .setSourceEntityId(sourceEntityId)
      .setText(text.toBnet())

    return EnvelopeProto.Envelope.newBuilder()
      .setReadable(readable)
      .build()
  }
}
