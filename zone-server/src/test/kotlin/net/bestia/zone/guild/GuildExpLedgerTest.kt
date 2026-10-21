package net.bestia.zone.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Test

class GuildExpLedgerTest {

  /** Runs every job on the caller, so a flush can be asserted as soon as it returns. */
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val writer = mockk<GuildExpWriter>(relaxed = true) {
    every { apply(any(), any()) } returns true
  }
  private val publisher = mockk<GuildInfoPublisher>(relaxed = true)
  private val sut = GuildExpLedger(jobs, writer, publisher)

  @Test
  fun `a flush writes what each member paid, summed, and pushes the guild`() {
    sut.add(GUILD, MASTER, 5)
    sut.add(GUILD, MASTER, 7)
    sut.add(GUILD, OTHER_MASTER, 1)

    sut.flush()

    verify { writer.apply(GUILD, mapOf(MASTER to 12L, OTHER_MASTER to 1L)) }
    verify { publisher.publishNow(GUILD) }
  }

  @Test
  fun `a flush writes nothing twice`() {
    sut.add(GUILD, MASTER, 5)
    sut.flush()
    sut.flush()

    verify(exactly = 1) { writer.apply(any(), any()) }
  }

  @Test
  fun `a disbanded guild's gathered EXP is dropped`() {
    sut.add(GUILD, MASTER, 5)
    sut.discard(GUILD)

    sut.flush()

    verify(exactly = 0) { writer.apply(any(), any()) }
  }

  @Test
  fun `a guild that is gone takes no push`() {
    every { writer.apply(GUILD, any()) } returns false
    sut.add(GUILD, MASTER, 5)

    sut.flush()

    verify(exactly = 0) { publisher.publishNow(any()) }
  }

  private companion object {
    const val GUILD = 1L
    const val MASTER = 10L
    const val OTHER_MASTER = 11L
  }
}
