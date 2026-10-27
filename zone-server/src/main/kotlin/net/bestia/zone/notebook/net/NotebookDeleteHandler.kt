package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import org.springframework.stereotype.Component

@Component
class NotebookDeleteHandler(
  private val notebook: NotebookService,
  private val replies: NotebookReplies,
) : IoMessageHandler<NotebookDeleteCMSG> {
  override val wire = decoder(MessageCase.NOTEBOOK_DELETE) { accountId, envelope ->
    val delete = envelope.notebookDelete
    NotebookDeleteCMSG(accountId, delete.collection, delete.key, if (delete.hasBaseRevision()) delete.baseRevision else null)
  }

  override fun handle(msg: NotebookDeleteCMSG): Boolean {
    val owner = replies.ownerOf(msg.playerId)
    val result = notebook.delete(owner, Client.GAME, msg.collection, msg.key, msg.baseRevision)
    replies.answer(msg.playerId, msg.collection, result)
    return true
  }
}
