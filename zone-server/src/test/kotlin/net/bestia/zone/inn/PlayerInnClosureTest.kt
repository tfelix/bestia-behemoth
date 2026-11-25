package net.bestia.zone.inn

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.prop.PlayerStructureDestroyedEvent
import net.bestia.zone.settlement.TownFellEvent
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class PlayerInnClosureTest {

  private val guest = mockk<Master>(relaxed = true) { every { id } returns GUEST }
  private val masters = mockk<MasterRepository> {
    every { findAllSavedAt(DOOR.x, DOOR.y, DOOR.z) } returns listOf(guest)
    every { findAllByHomeSettlementName(TOWN) } returns listOf(guest)
  }
  private val letters = mockk<LetterService>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val transactions = mockk<TransactionTemplate> {
    every { execute(any<TransactionCallback<Any>>()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk()) }
  }
  private val sut = PlayerInnClosure(masters, letters, jobs, transactions)

  @Test
  fun `a torn down inn clears the save point of everyone who slept there`() {
    sut.onDestroyed(PlayerStructureDestroyedEvent(this, structureId = 1L, kind = StaticEntityKind.INN, position = DOOR))

    verify { guest.spawnPosition = Vec3L.ZERO }
    verify { guest.homeSettlementName = "" }
    verify { letters.postNotice(GUEST, any(), any(), any()) }
  }

  @Test
  fun `another building going down leaves save points alone`() {
    sut.onDestroyed(PlayerStructureDestroyedEvent(this, structureId = 1L, kind = StaticEntityKind.POST_OFFICE, position = DOOR))

    verify(exactly = 0) { masters.findAllSavedAt(any(), any(), any()) }
  }

  @Test
  fun `a fallen town clears the save point of everyone at its inn`() {
    sut.onTownFell(TownFellEvent(this, townId = 3L, name = TOWN, sign = Vec3L(0, 0, 0)))

    verify { guest.spawnPosition = Vec3L.ZERO }
  }

  private companion object {
    const val GUEST = 7L
    const val TOWN = "Oakhold"
    val DOOR = Vec3L(10, 20, 3)
  }
}
