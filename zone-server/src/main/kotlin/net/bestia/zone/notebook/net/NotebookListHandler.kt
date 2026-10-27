package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import org.springframework.stereotype.Component

@Component
class NotebookListHandler(
  private val notebook: NotebookService,
  private val replies: NotebookReplies,
) : IoMessageHandler<NotebookListCMSG> {
  override val wire = decoder(MessageCase.NOTEBOOK_LIST) { accountId, envelope ->
    NotebookListCMSG(accountId, envelope.notebookList.collection)
  }

  override fun handle(msg: NotebookListCMSG): Boolean {
    replies.answer(msg.playerId, msg.collection, notebook.list(replies.ownerOf(msg.playerId), Client.GAME, msg.collection))
    return true
  }
}
