package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.NotebookProto
import net.bestia.zone.message.SMSG

/** One entry written or deleted; [json] is empty for a deletion. */
data class NotebookChangedSMSG(
  val collection: String,
  val key: String,
  val revision: Long,
  val deleted: Boolean,
  val json: String,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setNotebookChanged(
        NotebookProto.NotebookChangedSMSG.newBuilder()
          .setCollection(collection)
          .setKey(key)
          .setRevision(revision)
          .setDeleted(deleted)
          .setJson(json)
      )
      .build()
  }
}
