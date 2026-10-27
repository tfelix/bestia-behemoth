package net.bestia.api.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * The player's notebook - routes, map marks, map preferences - the same store the game reads and writes. A master's
 * collections live under the master, the account's own under `/v1/companion/notebook`. Every write is told to the
 * game and to the app's live socket as a `NotebookChanged` event, this app's own included.
 *
 * A 409 is a write against an entry that changed since [Write.baseRevision]: list it again. A 400 carries the
 * client's error code, NOTEBOOK_FULL or NOTEBOOK_INVALID.
 */
@RestController
class NotebookController(
  private val zone: ZoneClient,
) {

  /** [value] is the entry as its collection shows it. */
  data class Entry(val key: String, val revision: Long, val value: JsonNode)

  data class Page(val revision: Long, val entries: List<Entry>)

  /** With [baseRevision] the write lands only while the entry is still at it, 0 for one that must not exist yet. */
  data class Write(val value: JsonNode, val baseRevision: Long? = null)

  /** [value] is null for a deletion. */
  data class Written(val revision: Long, val key: String, val value: JsonNode?)

  @GetMapping("/v1/companion/masters/{masterId}/notebook/{collection}")
  fun list(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable collection: String,
  ): Page {
    return pageOf(zone.ask(accountId, ZoneQuery.NotebookList(masterId, collection)).answerOrThrow())
  }

  @PostMapping("/v1/companion/masters/{masterId}/notebook/{collection}")
  @ResponseStatus(HttpStatus.CREATED)
  fun add(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable collection: String,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody write: Write,
  ): Written {
    return put(accountId, masterId, collection, null, write, idempotencyKey)
  }

  @PutMapping("/v1/companion/masters/{masterId}/notebook/{collection}/{key}")
  fun put(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable collection: String,
    @PathVariable key: String,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody write: Write,
  ): Written {
    return put(accountId, masterId, collection, key, write, idempotencyKey)
  }

  @DeleteMapping("/v1/companion/masters/{masterId}/notebook/{collection}/{key}")
  fun delete(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable collection: String,
    @PathVariable key: String,
    @RequestParam(required = false) baseRevision: Long?,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): Written {
    return writtenOf(zone.ask(accountId, ZoneQuery.NotebookDelete(masterId, collection, key, baseRevision), keyOf(idempotencyKey)))
  }

  @GetMapping("/v1/companion/notebook/{collection}")
  fun listOwn(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable collection: String): Page {
    return pageOf(zone.ask(accountId, ZoneQuery.NotebookList(null, collection)).answerOrThrow())
  }

  @PutMapping("/v1/companion/notebook/{collection}/{key}")
  fun putOwn(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable collection: String,
    @PathVariable key: String,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody write: Write,
  ): Written {
    return put(accountId, null, collection, key, write, idempotencyKey)
  }

  @DeleteMapping("/v1/companion/notebook/{collection}/{key}")
  fun deleteOwn(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable collection: String,
    @PathVariable key: String,
    @RequestParam(required = false) baseRevision: Long?,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): Written {
    return writtenOf(zone.ask(accountId, ZoneQuery.NotebookDelete(null, collection, key, baseRevision), keyOf(idempotencyKey)))
  }

  private fun put(accountId: Long, masterId: Long?, collection: String, key: String?, write: Write, idempotencyKey: String): Written {
    val query = ZoneQuery.NotebookPut(masterId, collection, key, mapper.writeValueAsString(write.value), write.baseRevision)
    return writtenOf(zone.ask(accountId, query, keyOf(idempotencyKey)))
  }

  private fun pageOf(notebook: ZoneAnswer.Notebook): Page {
    return Page(notebook.revision, notebook.entries.map { Entry(it.key, it.revision, mapper.readTree(it.json)) })
  }

  private fun writtenOf(reply: BusReply): Written {
    if (reply.status == BusReply.Status.INVALID && reply.detail == STALE) {
      throw ResponseStatusException(HttpStatus.CONFLICT, STALE)
    }
    val written = reply.answerOrThrow<ZoneAnswer.NotebookWritten>()
    return Written(written.revision, written.key, written.json?.let { mapper.readTree(it) })
  }

  private fun keyOf(idempotencyKey: String): String {
    return idempotencyKey.take(MAX_KEY_LENGTH)
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
    const val STALE = "NOTEBOOK_STALE"
    val mapper = jacksonObjectMapper()
  }
}
