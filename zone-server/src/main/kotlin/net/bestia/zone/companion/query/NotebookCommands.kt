package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import net.bestia.zone.notebook.NotebookService.Refusal
import net.bestia.zone.notebook.NotebookService.Result
import org.springframework.stereotype.Service

/**
 * The player's notebook as the companion app reads and writes it - the same store, rules and events the game's
 * notebook messages use. A refusal is INVALID carrying the client's error code, `NOTEBOOK_STALE` among them.
 */
@Service
class NotebookCommands(
  private val owned: OwnedMasters,
  private val notebook: NotebookService,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.NotebookList): BusReply {
    val owner = ownerOf(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    return reply(correlationId, notebook.list(owner, Client.COMPANION, query.collection))
  }

  fun put(correlationId: String, accountId: Long, query: ZoneQuery.NotebookPut): BusReply {
    val owner = ownerOf(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    val result = notebook.put(owner, Client.COMPANION, query.collection, query.key, query.json, query.baseRevision)
    return reply(correlationId, result)
  }

  fun delete(correlationId: String, accountId: Long, query: ZoneQuery.NotebookDelete): BusReply {
    val owner = ownerOf(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    return reply(correlationId, notebook.delete(owner, Client.COMPANION, query.collection, query.key, query.baseRevision))
  }

  /** Null when [masterId] is not the account's. */
  private fun ownerOf(accountId: Long, masterId: Long?): NotebookService.Owner? {
    if (masterId != null && owned.find(accountId, masterId) == null) return null
    return NotebookService.Owner(accountId, masterId)
  }

  private fun reply(correlationId: String, result: Result): BusReply {
    return when (result) {
      is Result.Listed -> BusReply.ok(
        correlationId,
        ZoneAnswer.Notebook(result.revision, result.entries.map { ZoneAnswer.Notebook.Entry(it.key, it.revision, it.body) })
      )
      is Result.Written -> BusReply.ok(correlationId, ZoneAnswer.NotebookWritten(result.revision, result.entry.key, result.entry.body))
      is Result.Deleted -> BusReply.ok(correlationId, ZoneAnswer.NotebookWritten(result.revision, result.key, null))
      is Result.Refused -> when (result.refusal) {
        Refusal.UNKNOWN_COLLECTION, Refusal.FORBIDDEN, Refusal.NOT_FOUND -> BusReply.of(correlationId, Status.NOT_FOUND)
        Refusal.INVALID -> BusReply.of(correlationId, Status.INVALID, "NOTEBOOK_INVALID")
        Refusal.FULL -> BusReply.of(correlationId, Status.INVALID, "NOTEBOOK_FULL")
        Refusal.STALE -> BusReply.of(correlationId, Status.INVALID, STALE)
      }
    }
  }

  companion object {
    /** The detail a stale write is refused with, which the api-server answers as a conflict. */
    const val STALE = "NOTEBOOK_STALE"
  }
}
