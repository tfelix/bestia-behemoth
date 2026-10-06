package net.bestia.zone.account.persistence

import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.master.MasterDeletionService
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.party.findByIdOrThrow
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import kotlin.test.assertEquals

/**
 * `Master.party` is set on the owner too, so a party read back from the database lists its owner among its
 * members. Seats and notifications must still count every master once.
 *
 * Uses throwaway masters on the fixture's account 3, like `MasterDeletionServiceTest`, and deletes them again.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class PartySizeTest {

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var partyRepository: PartyRepository

  @Autowired
  private lateinit var masterDeletionService: MasterDeletionService

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val transactionTemplate: TransactionTemplate by lazy { TransactionTemplate(transactionManager) }

  @Test
  fun `a party read back from the database counts each master once`() {
    val owner = createMaster("sizeOwner")
    val member = createMaster("sizeMember")
    try {
      val partyId = transactionTemplate.execute {
        val managedOwner = masterRepository.findByIdOrThrow(owner.id)
        val party = partyRepository.save(Party(owner = managedOwner, name = "size-${owner.id}"))
        masterRepository.findByIdOrThrow(member.id).party = party
        party.id
      }!!

      val (size, everyone) = transactionTemplate.execute {
        val party = partyRepository.findByIdOrThrow(partyId)
        party.size to party.everyone.map { it.id }.sorted()
      }!!

      assertEquals(2, size)
      assertEquals(listOf(owner.id, member.id).sorted(), everyone)
    } finally {
      masterDeletionService.delete(ACCOUNT_ID, member.id, member.name)
      masterDeletionService.delete(ACCOUNT_ID, owner.id, owner.name)
    }
  }

  private fun createMaster(name: String): Master {
    return masterFactory.create(
      ACCOUNT_ID,
      MasterFactory.CreateMasterData(
        name = name,
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt()
      )
    )
  }

  private companion object {
    /** Account 3 of the fixture starts with one master, so two more fit in its three slots. */
    const val ACCOUNT_ID = 3L
  }
}
