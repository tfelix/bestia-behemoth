package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.BestiaTag
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BestiaRenamingTest {

  private val world = testWorld()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val connections = mockk<ConnectionInfoService> { every { getMasterId(ACCOUNT) } returns MASTER }
  private val rows = mutableMapOf<Long, PlayerBestia>()
  private val playerBestias = mockk<PlayerBestiaRepository> {
    every { findByIdForUpdate(any()) } answers { rows[firstArg()] }
  }
  private val transactions = mockk<TransactionTemplate> {
    every { execute(any<TransactionCallback<Any?>>()) } answers {
      firstArg<TransactionCallback<Any?>>().doInTransaction(mockk(relaxed = true))
    }
  }

  private val renaming = BestiaRenaming(
    connections = connections,
    worldView = world,
    playerBestias = playerBestias,
    ownedBestias = mockk(relaxed = true),
    out = out,
    asyncJobs = mockk { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    transactions = transactions,
  )

  @Test
  fun `two bestias may share a name, and the tag everyone reads carries it with the owner`() {
    val rex = bestia(playerBestiaId = 1)
    val other = bestia(playerBestiaId = 2)

    renaming.rename(ACCOUNT, rex, "  Rex  ")
    renaming.rename(ACCOUNT, other, "Rex")

    assertEquals("Rex", rows.getValue(1).name)
    assertEquals("Rex", rows.getValue(2).name)
    assertEquals(BestiaTag("Rex", OWNER_NAME), world.get(rex, BestiaTag::class))
    assertEquals(BestiaTag("Rex", OWNER_NAME), world.get(other, BestiaTag::class))
  }

  @Test
  fun `a blank name gives it back its species' name`() {
    val rex = bestia(playerBestiaId = 1)
    renaming.rename(ACCOUNT, rex, "Rex")

    renaming.rename(ACCOUNT, rex, "   ")

    assertNull(rows.getValue(1).name)
    assertNull(world.get(rex, BestiaTag::class)!!.name)
  }

  @Test
  fun `a name of symbols is refused and changes nothing`() {
    val rex = bestia(playerBestiaId = 1)
    val refusal = slot<OperationErrorSMSG>()
    every { out.sendToPlayer(ACCOUNT, capture(refusal)) } returns Unit

    renaming.rename(ACCOUNT, rex, "<Rex!>")

    assertEquals(OpError.BESTIA_NAME_INVALID, refusal.captured.code)
    verify(exactly = 0) { playerBestias.findByIdForUpdate(any()) }
  }

  private fun bestia(playerBestiaId: Long): Long {
    var name: String? = null
    rows[playerBestiaId] = mockk(relaxed = true) {
      every { master.id } returns MASTER
      every { master.name } returns OWNER_NAME
      every { this@mockk.name } answers { name }
      every { this@mockk.name = any() } answers { name = firstArg() }
    }

    return world.createEntity { id -> add(id, OwnedBestia(masterId = MASTER, playerBestiaId = playerBestiaId)) }
  }

  private companion object {
    const val ACCOUNT = 3L
    const val MASTER = 30L
    const val OWNER_NAME = "Ann"
  }
}
