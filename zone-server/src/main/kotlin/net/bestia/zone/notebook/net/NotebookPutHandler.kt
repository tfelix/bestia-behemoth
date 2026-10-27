package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import org.springframework.stereotype.Component

@Component
class NotebookPutHandler(
  private val notebook: NotebookService,
  private val replies: NotebookReplies,
) : IoMessageHandler<NotebookPutCMSG> {
  override val wire = decoder(MessageCase.NOTEBOOK_PUT) { accountId, envelope ->
    val put = envelope.notebookPut
    NotebookPutCMSG(
      accountId,
      put.collection,
      put.key.ifEmpty { null },
      put.json,
      if (put.hasBaseRevision()) put.baseRevision else null,
    )
  }

  override fun handle(msg: NotebookPutCMSG): Boolean {
    val owner = replies.ownerOf(msg.playerId)
    val result = notebook.put(owner, Client.GAME, msg.collection, msg.key, msg.json, msg.baseRevision)
    replies.answer(msg.playerId, msg.collection, result)
    return true
  }
}
