package net.bestia.zone.notebook

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookCollection.Scope
import net.bestia.zone.notebook.NotebookCollection.Shape
import net.bestia.zone.notebook.persistence.NotebookEntry
import net.bestia.zone.notebook.persistence.NotebookEntryRepository
import net.bestia.zone.notebook.persistence.NotebookHead
import net.bestia.zone.notebook.persistence.NotebookHeadRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

/**
 * What players keep - routes, marks, preferences - for the game and the companion alike, in one store rather than
 * a table, a message set and an endpoint per feature. Off the tick: every call reads or writes the database.
 *
 * Every write to one owner's collection takes the next [NotebookHead.revision] and publishes a
 * [NotebookChangedEvent] with it, which is how the other app hears of it at once and how a client that missed an
 * event knows it did.
 */
@Service
class NotebookService(
  collections: List<NotebookCollection<*>>,
  private val entries: NotebookEntryRepository,
  private val heads: NotebookHeadRepository,
  private val events: ApplicationEventPublisher,
) {

  /** Who is asking: the account, and the master it is acting as, when there is one. */
  data class Owner(val accountId: Long, val masterId: Long?)

  /** [body] is the entry as clients are shown it. */
  data class Entry(val key: String, val revision: Long, val body: String)

  sealed interface Result {
    data class Listed(val revision: Long, val entries: List<Entry>) : Result
    data class Written(val revision: Long, val entry: Entry) : Result
    data class Deleted(val revision: Long, val key: String) : Result
    data class Refused(val refusal: Refusal) : Result
  }

  enum class Refusal {
    UNKNOWN_COLLECTION,

    /** The app may not do this to this collection, or a master's collection was asked for with no master. */
    FORBIDDEN,
    INVALID,
    FULL,

    /** The entry changed since the revision the client wrote against. */
    STALE,
    NOT_FOUND,
  }

  private val byId = collections.associateBy { it.id }

  // A client may send back an entry as it was shown, derived fields and all; those are dropped, not refused.
  private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  private val random = SecureRandom()

  fun collection(id: String): NotebookCollection<*>? {
    return byId[id]
  }

  @Transactional(readOnly = true)
  fun list(owner: Owner, client: Client, collectionId: String): Result {
    val collection = byId[collectionId] ?: return Result.Refused(Refusal.UNKNOWN_COLLECTION)
    val ownerId = ownerIdOf(owner, collection)
    if (ownerId == null || !collection.mayRead(client)) return Result.Refused(Refusal.FORBIDDEN)

    val revision = heads.findByOwnerScopeAndOwnerIdAndCollection(collection.scope, ownerId, collection.id)?.revision ?: 0
    val listed = entries.findAllByOwnerScopeAndOwnerIdAndCollectionOrderById(collection.scope, ownerId, collection.id)
      .map { Entry(it.entryKey, it.revision, presented(collection, it.body)) }
    return Result.Listed(revision, listed)
  }

  /**
   * Keeps [body] under [key], or under a new key when [key] is null. With [baseRevision] the write only lands
   * while the entry is still at that revision - 0 for one that must not exist yet; without, the last write wins.
   */
  @Transactional
  fun put(owner: Owner, client: Client, collectionId: String, key: String?, body: String, baseRevision: Long?): Result {
    val collection = byId[collectionId] ?: return Result.Refused(Refusal.UNKNOWN_COLLECTION)
    val ownerId = ownerIdOf(owner, collection)
    if (ownerId == null || !collection.mayWrite(client)) return Result.Refused(Refusal.FORBIDDEN)

    val entryKey = when (collection.shape) {
      Shape.SINGLE -> NotebookCollection.SINGLE_KEY
      Shape.KEYED -> key ?: newKey()
    }
    if (!KEY.matches(entryKey)) return Result.Refused(Refusal.INVALID)

    val stored = canonical(collection, body) ?: return Result.Refused(Refusal.INVALID)

    val head = lockedHead(collection, ownerId)
    val existing = entries.findByOwnerScopeAndOwnerIdAndCollectionAndEntryKey(collection.scope, ownerId, collection.id, entryKey)
    if (baseRevision != null && baseRevision != (existing?.revision ?: 0)) return Result.Refused(Refusal.STALE)
    if (existing == null &&
      entries.countByOwnerScopeAndOwnerIdAndCollection(collection.scope, ownerId, collection.id) >= collection.maxEntries
    ) {
      return Result.Refused(Refusal.FULL)
    }

    head.revision += 1
    val entry = existing ?: NotebookEntry(collection.scope, ownerId, collection.id, entryKey)
    entry.body = stored
    entry.revision = head.revision
    entry.updatedAt = Instant.now()
    entries.save(entry)

    val written = Entry(entryKey, head.revision, presented(collection, stored))
    events.publishEvent(
      NotebookChangedEvent(owner.accountId, masterOf(owner, collection), collection.id, entryKey, head.revision, false, written.body)
    )
    return Result.Written(head.revision, written)
  }

  @Transactional
  fun delete(owner: Owner, client: Client, collectionId: String, key: String, baseRevision: Long?): Result {
    val collection = byId[collectionId] ?: return Result.Refused(Refusal.UNKNOWN_COLLECTION)
    val ownerId = ownerIdOf(owner, collection)
    if (ownerId == null || !collection.mayWrite(client)) return Result.Refused(Refusal.FORBIDDEN)

    val head = lockedHead(collection, ownerId)
    val existing = entries.findByOwnerScopeAndOwnerIdAndCollectionAndEntryKey(collection.scope, ownerId, collection.id, key)
      ?: return Result.Refused(Refusal.NOT_FOUND)
    if (baseRevision != null && baseRevision != existing.revision) return Result.Refused(Refusal.STALE)

    head.revision += 1
    entries.delete(existing)

    events.publishEvent(
      NotebookChangedEvent(owner.accountId, masterOf(owner, collection), collection.id, key, head.revision, true, null)
    )
    return Result.Deleted(head.revision, key)
  }

  /** One entry read as its collection's type, for server code that acts on it - sending a bestia along a route. */
  @Transactional(readOnly = true)
  fun <T : Any> read(owner: Owner, collection: NotebookCollection<T>, key: String): T? {
    val ownerId = ownerIdOf(owner, collection) ?: return null
    val entry = entries.findByOwnerScopeAndOwnerIdAndCollectionAndEntryKey(collection.scope, ownerId, collection.id, key)
      ?: return null
    return mapper.readValue(entry.body, collection.type)
  }

  private fun ownerIdOf(owner: Owner, collection: NotebookCollection<*>): Long? {
    return when (collection.scope) {
      Scope.MASTER -> owner.masterId
      Scope.ACCOUNT -> owner.accountId
    }
  }

  /** The master a change belongs to, for the event: none for a collection of the whole account. */
  private fun masterOf(owner: Owner, collection: NotebookCollection<*>): Long? {
    return if (collection.scope == Scope.MASTER) owner.masterId else null
  }

  /** The head, created at revision 0 for a collection's first write, and locked until the transaction ends. */
  private fun lockedHead(collection: NotebookCollection<*>, ownerId: Long): NotebookHead {
    return heads.findForUpdateByOwnerScopeAndOwnerIdAndCollection(collection.scope, ownerId, collection.id)
      ?: heads.saveAndFlush(NotebookHead(collection.scope, ownerId, collection.id, 0))
  }

  /** [body] read as the collection's type and written back, so what is stored is only what the type holds. */
  private fun <T : Any> canonical(collection: NotebookCollection<T>, body: String): String? {
    if (body.toByteArray().size > collection.maxBodyBytes) return null

    val value = try {
      mapper.readValue(body, collection.type)
    } catch (e: Exception) {
      LOG.debug { "Refused a ${collection.id} entry that does not read: ${e.message}" }
      return null
    }

    val problem = collection.validate(value)
    if (problem != null) {
      LOG.debug { "Refused a ${collection.id} entry: $problem" }
      return null
    }
    return mapper.writeValueAsString(value)
  }

  private fun <T : Any> presented(collection: NotebookCollection<T>, stored: String): String {
    return mapper.writeValueAsString(collection.present(mapper.readValue(stored, collection.type)))
  }

  private fun newKey(): String {
    return (1..NEW_KEY_LENGTH).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }.joinToString("")
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    val KEY = Regex("""[A-Za-z0-9_-]{1,40}""")
    const val KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    const val NEW_KEY_LENGTH = 10
  }
}
