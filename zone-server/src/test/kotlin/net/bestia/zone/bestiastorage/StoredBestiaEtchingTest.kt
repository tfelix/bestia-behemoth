package net.bestia.zone.bestiastorage

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.master.bestia.BestiaEtching
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredBestiaEtchingTest {

  private val world = testWorld()

  private val tusk = mockk<PlayerBestia> {
    every { id } returns TUSK
    every { bestia.id } returns BOAR
    every { level } returns 44
    every { name } returns "Tusk"
    every { master.id } returns MASTER_ID
    every { isStored } returns true
  }

  private val masters = mockk<MasterRepository> {
    every { findById(MASTER_ID) } returns Optional.of(mockk<Master> { every { bestias.storedBestias } returns listOf(tusk) })
  }

  private val playerBestias = mockk<PlayerBestiaRepository> { every { findById(TUSK) } returns Optional.of(tusk) }

  private val ledger = mockk<BestiaLedgerScript> { every { grants(any(), any()) } returns true }

  private val etching = mockk<BestiaEtching> {
    every { tierOfGem(CUT_RUBY) } returns GemTier.RUBY
    every { tierOfGem(CUT_PYRELITH) } returns GemTier.PYRELITH
    every { fits(BOAR, 44, GemTier.RUBY) } returns true
    every { fits(BOAR, 44, GemTier.PYRELITH) } returns false
  }

  private val loss = mockk<PlayerBestiaLoss>(relaxed = true)

  private val transactions = mockk<TransactionTemplate> {
    every { execute(any<TransactionCallback<Any>>()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk()) }
  }

  private val stored = StoredBestiaEtching(masters, playerBestias, ledger, etching, loss, transactions)

  @Test
  fun `the storage opens to the ledger and not in a fight`() {
    val master = world.createEntity { }
    assertNull(stored.accessRefusal(world, master))

    world.add(master, InCombat())
    assertEquals(OpError.BESTIA_STORAGE_IN_COMBAT, stored.accessRefusal(world, master))
  }

  @Test
  fun `a loaded Bestia is judged by its own level against the gem`() {
    stored.load(MASTER_ID)

    assertNull(stored.refusal(MASTER_ID, TUSK, CUT_RUBY))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, stored.refusal(MASTER_ID, TUSK, CUT_PYRELITH))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, stored.refusal(MASTER_ID, TUSK + 1, CUT_RUBY), "one the window never listed")
  }

  @Test
  fun `binding loses the stored Bestia for good and answers its essence`() {
    stored.load(MASTER_ID)

    assertEquals(Essence(BOAR, GemTier.RUBY), stored.bind(MASTER_ID, TUSK, CUT_RUBY))
    verify { loss.loseStored(TUSK) }
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, stored.refusal(MASTER_ID, TUSK, CUT_RUBY), "it is gone from the list")
  }

  private companion object {
    const val MASTER_ID = 42L
    const val TUSK = 900L
    const val BOAR = 6L
    const val CUT_RUBY = 138L
    const val CUT_PYRELITH = 139L
  }
}
