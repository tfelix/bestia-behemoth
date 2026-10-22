package net.bestia.zone.compendium

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.MasterPresenceChangedEvent
import org.junit.jupiter.api.Test

class CompendiumRecorderTest {

  private val store = mockk<CompendiumStore> {
    every { load(MASTER_ID) } returns setOf(KNOWN_SPECIES)
    every { recordSeen(any(), any()) } returns true
    every { recordCaught(any(), any()) } returns true
  }
  private val publisher = mockk<CompendiumPublisher>(relaxed = true)

  /** Runs every job at once, so a test reads straight down. */
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val sut = CompendiumRecorder(CompendiumLedger(), store, publisher, jobs)

  @Test
  fun `a first sighting is stored and published once`() {
    online()

    sut.sighted(ACCOUNT_ID, MASTER_ID, NEW_SPECIES)
    sut.sighted(ACCOUNT_ID, MASTER_ID, NEW_SPECIES)

    verify(exactly = 1) { store.recordSeen(MASTER_ID, NEW_SPECIES) }
    verify(exactly = 1) { publisher.publish(ACCOUNT_ID, MASTER_ID) }
  }

  @Test
  fun `a species already in the book is not stored again`() {
    online()

    sut.sighted(ACCOUNT_ID, MASTER_ID, KNOWN_SPECIES)

    verify(exactly = 0) { store.recordSeen(any(), any()) }
  }

  @Test
  fun `sightings for a master not being played are ignored`() {
    sut.sighted(ACCOUNT_ID, MASTER_ID, NEW_SPECIES)

    verify(exactly = 0) { store.recordSeen(any(), any()) }
  }

  @Test
  fun `a catch is stored even for a master that went offline`() {
    online()
    sut.onPresenceChanged(MasterPresenceChangedEvent(this, ACCOUNT_ID, MASTER_ID, online = false))

    sut.caught(ACCOUNT_ID, MASTER_ID, NEW_SPECIES)

    verify(exactly = 1) { store.recordCaught(MASTER_ID, NEW_SPECIES) }
  }

  private fun online() {
    sut.onPresenceChanged(MasterPresenceChangedEvent(this, ACCOUNT_ID, MASTER_ID, online = true))
  }

  private companion object {
    const val ACCOUNT_ID = 3L
    const val MASTER_ID = 7L
    const val KNOWN_SPECIES = 1L
    const val NEW_SPECIES = 2L
  }
}
