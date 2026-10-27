package net.bestia.zone.notebook

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.notebook.NotebookCollection.Access
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookCollection.Scope
import net.bestia.zone.notebook.NotebookCollection.Shape
import net.bestia.zone.notebook.NotebookService.Owner
import net.bestia.zone.notebook.NotebookService.Refusal
import net.bestia.zone.notebook.NotebookService.Result
import net.bestia.zone.notebook.persistence.NotebookEntry
import net.bestia.zone.notebook.persistence.NotebookEntryRepository
import net.bestia.zone.notebook.persistence.NotebookHead
import net.bestia.zone.notebook.persistence.NotebookHeadRepository
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotebookServiceTest {

  data class Note(val text: String = "")

  /** Two notes at most, and only the game may write them - as key bindings would be. */
  private class Notes : NotebookCollection<Note>(
    id = "notes", type = Note::class.java, scope = Scope.ACCOUNT, shape = Shape.KEYED, maxEntries = 2,
    maxBodyBytes = 64, access = mapOf(Client.GAME to Access.WRITE, Client.COMPANION to Access.READ),
  ) {
    override fun validate(value: Note): String? {
      return if (value.text.isBlank()) "blank" else null
    }

    override fun present(value: Note): Any {
      return mapOf("text" to value.text, "length" to value.text.length)
    }
  }

  private class Prefs : NotebookCollection<Note>(
    id = "prefs", type = Note::class.java, scope = Scope.MASTER, shape = Shape.SINGLE, maxEntries = 1,
    maxBodyBytes = 64, access = mapOf(Client.GAME to Access.WRITE, Client.COMPANION to Access.WRITE),
  )

  private val rows = ArrayList<NotebookEntry>()
  private val headRows = ArrayList<NotebookHead>()
  private val published = ArrayList<NotebookChangedEvent>()

  private val entries: NotebookEntryRepository = mockk(relaxed = true) {
    val saved = slot<NotebookEntry>()
    every { save(capture(saved)) } answers { saved.captured.also { if (it !in rows) rows += it } }
    every { delete(any()) } answers { rows.remove(firstArg<NotebookEntry>()) }
    every { findAllByOwnerScopeAndOwnerIdAndCollectionOrderById(any(), any(), any()) } answers {
      rows.filter { it.ownerScope == firstArg() && it.ownerId == secondArg() && it.collection == thirdArg() }
    }
    every { findByOwnerScopeAndOwnerIdAndCollectionAndEntryKey(any(), any(), any(), any()) } answers {
      rows.firstOrNull { it.ownerScope == firstArg() && it.ownerId == secondArg() && it.collection == thirdArg() && it.entryKey == arg(3) }
    }
    every { countByOwnerScopeAndOwnerIdAndCollection(any(), any(), any()) } answers {
      rows.count { it.ownerScope == firstArg() && it.ownerId == secondArg() && it.collection == thirdArg() }.toLong()
    }
  }

  private val heads: NotebookHeadRepository = mockk {
    val saved = slot<NotebookHead>()
    every { saveAndFlush(capture(saved)) } answers { saved.captured.also { headRows += it } }
    every { findForUpdateByOwnerScopeAndOwnerIdAndCollection(any(), any(), any()) } answers {
      headRows.firstOrNull { it.ownerScope == firstArg() && it.ownerId == secondArg() && it.collection == thirdArg() }
    }
    every { findByOwnerScopeAndOwnerIdAndCollection(any(), any(), any()) } answers {
      headRows.firstOrNull { it.ownerScope == firstArg() && it.ownerId == secondArg() && it.collection == thirdArg() }
    }
  }

  private val events: ApplicationEventPublisher = mockk {
    every { publishEvent(any<Any>()) } answers { published += firstArg<NotebookChangedEvent>() }
  }

  private val service = NotebookService(listOf(Notes(), Prefs()), entries, heads, events)
  private val player = Owner(accountId = 7, masterId = 70)

  @Test
  fun `each write takes the next revision and is published once, shown as the collection shows it`() {
    val first = assertIs<Result.Written>(service.put(player, Client.GAME, "notes", null, """{"text":"ore"}""", null))
    val second = assertIs<Result.Written>(service.put(player, Client.GAME, "notes", first.entry.key, """{"text":"iron"}""", 1))

    assertEquals(1, first.revision)
    assertEquals(2, second.revision)
    assertEquals("""{"text":"iron","length":4}""", second.entry.body)
    assertEquals(listOf(1L, 2L), published.map { it.revision })
    assertEquals(null, published.first().masterId, "an account collection is no one master's")

    val listed = assertIs<Result.Listed>(service.list(player, Client.COMPANION, "notes"))
    assertEquals(2, listed.revision)
    assertEquals(listOf(first.entry.key), listed.entries.map { it.key })
  }

  @Test
  fun `a write against an older revision is stale and changes nothing`() {
    val first = assertIs<Result.Written>(service.put(player, Client.GAME, "notes", null, """{"text":"ore"}""", null))
    service.put(player, Client.GAME, "notes", first.entry.key, """{"text":"iron"}""", first.revision)

    val stale = service.put(player, Client.GAME, "notes", first.entry.key, """{"text":"gold"}""", first.revision)

    assertEquals(Result.Refused(Refusal.STALE), stale)
    assertEquals(2, published.size, "a refused write is not published")
    assertTrue(rows.single().body.contains("iron"))
  }

  @Test
  fun `an app writes only what its collection lets it`() {
    assertEquals(Result.Refused(Refusal.FORBIDDEN), service.put(player, Client.COMPANION, "notes", null, """{"text":"x"}""", null))
    assertEquals(Result.Refused(Refusal.UNKNOWN_COLLECTION), service.list(player, Client.GAME, "spells"))
    assertEquals(
      Result.Refused(Refusal.FORBIDDEN),
      service.list(Owner(7, masterId = null), Client.GAME, "prefs"),
      "a master's collection with no master selected"
    )
    assertTrue(published.isEmpty())
  }

  @Test
  fun `a collection refuses what it does not accept, and more entries than it holds`() {
    assertEquals(Result.Refused(Refusal.INVALID), service.put(player, Client.GAME, "notes", null, """{"text":" "}""", null))
    assertEquals(Result.Refused(Refusal.INVALID), service.put(player, Client.GAME, "notes", null, "not json", null))
    assertEquals(Result.Refused(Refusal.INVALID), service.put(player, Client.GAME, "notes", "bad key!", """{"text":"x"}""", null))
    assertEquals(
      Result.Refused(Refusal.INVALID),
      service.put(player, Client.GAME, "notes", null, """{"text":"${"x".repeat(80)}"}""", null),
      "longer than the collection's body limit"
    )

    service.put(player, Client.GAME, "notes", null, """{"text":"a"}""", null)
    service.put(player, Client.GAME, "notes", null, """{"text":"b"}""", null)
    assertEquals(Result.Refused(Refusal.FULL), service.put(player, Client.GAME, "notes", null, """{"text":"c"}""", null))
  }

  @Test
  fun `a single document has one key, and fields it does not hold are dropped`() {
    val written = assertIs<Result.Written>(service.put(player, Client.COMPANION, "prefs", "anything", """{"text":"x","extra":1}""", null))

    assertEquals(NotebookCollection.SINGLE_KEY, written.entry.key)
    assertEquals("""{"text":"x"}""", rows.single().body)
    assertEquals(70L, published.single().masterId)
  }

  @Test
  fun `a deletion is published and frees the key`() {
    val first = assertIs<Result.Written>(service.put(player, Client.GAME, "notes", "k1", """{"text":"ore"}""", 0))

    assertEquals(Result.Refused(Refusal.STALE), service.put(player, Client.GAME, "notes", "k1", """{"text":"x"}""", 0), "0 is 'must not exist yet'")
    assertIs<Result.Deleted>(service.delete(player, Client.GAME, "notes", first.entry.key, first.revision))
    assertTrue(published.last().deleted)
    assertEquals(Result.Refused(Refusal.NOT_FOUND), service.delete(player, Client.GAME, "notes", "k1", null))
  }
}
