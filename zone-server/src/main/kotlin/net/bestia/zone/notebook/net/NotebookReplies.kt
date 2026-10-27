package net.bestia.zone.notebook.net

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import net.bestia.zone.notebook.NotebookService.Refusal
import net.bestia.zone.notebook.NotebookService.Result
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/**
 * What the notebook handlers share: whose notebook a game connection writes to, and how an answer is sent. A
 * write that lands is not answered here - the client hears of its own write the way it hears of the companion's,
 * through [NotebookPushListener].
 */
@Component
class NotebookReplies(
  private val connections: ConnectionInfoService,
  private val notebook: NotebookService,
  private val out: OutMessageProcessor,
) {

  fun ownerOf(accountId: Long): NotebookService.Owner {
    return NotebookService.Owner(accountId, connections.activeMasterIdOf(accountId))
  }

  fun answer(accountId: Long, collection: String, result: Result) {
    when (result) {
      is Result.Listed -> out.sendToPlayer(accountId, NotebookSMSG(collection, result.revision, result.entries))
      is Result.Refused -> refuse(accountId, collection, result.refusal)
      is Result.Written, is Result.Deleted -> Unit
    }
  }

  /** A stale write is followed by the collection as it now is, so the client shows the newer version. */
  private fun refuse(accountId: Long, collection: String, refusal: Refusal) {
    val code = when (refusal) {
      Refusal.FULL -> OpError.NOTEBOOK_FULL
      Refusal.STALE, Refusal.NOT_FOUND -> OpError.NOTEBOOK_STALE
      Refusal.UNKNOWN_COLLECTION, Refusal.FORBIDDEN, Refusal.INVALID -> OpError.NOTEBOOK_INVALID
    }
    out.sendToPlayer(accountId, OperationErrorSMSG(code))

    if (code == OpError.NOTEBOOK_STALE) {
      answer(accountId, collection, notebook.list(ownerOf(accountId), Client.GAME, collection))
    }
  }
}
