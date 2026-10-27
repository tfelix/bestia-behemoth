package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.NotebookProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.notebook.NotebookService

/** Every entry of one collection, and the revision it is at. */
data class NotebookSMSG(
  val collection: String,
  val revision: Long,
  val entries: List<NotebookService.Entry>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val listed = NotebookProto.NotebookSMSG.newBuilder()
      .setCollection(collection)
      .setRevision(revision)
    for (entry in entries) {
      listed.addEntries(
        NotebookProto.NotebookEntry.newBuilder().setKey(entry.key).setRevision(entry.revision).setJson(entry.body)
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setNotebook(listed)
      .build()
  }
}
