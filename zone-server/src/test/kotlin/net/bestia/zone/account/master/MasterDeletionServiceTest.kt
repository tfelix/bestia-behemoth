package net.bestia.zone.account.master

import net.bestia.zone.bestia.PlayerBestiaFactory
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.instance.ItemInstanceRepository
import net.bestia.zone.party.Party
import net.bestia.zone.party.PartyRepository
import net.bestia.zone.party.PartyService
import net.bestia.zone.scenarios.ScenarioDataSetup
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every test creates its own throwaway master instead of deleting one of [ScenarioDataSetup]'s: the fixture
 * is built once for the whole Spring context and shared by every test in it, so deleting out of it would
 * quietly remove data other tests still expect to be there. For the same reason a test that ends with its
 * master still standing tidies it away itself - the account only has so many slots.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class MasterDeletionServiceTest {

  @Autowired
  private lateinit var masterDeletionService: MasterDeletionService

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaFactory: PlayerBestiaFactory

  @Autowired
  private lateinit var playerBestiaRepository: PlayerBestiaRepository

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var partyRepository: PartyRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  /** Only needed to set a party up; [MasterDeletionService] brings its own transaction. */
  private val transactionTemplate: TransactionTemplate by lazy { TransactionTemplate(transactionManager) }

  @Test
  fun `deletes the master and frees its name`() {
    val master = createThrowawayMaster()

    val result = masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)

    assertTrue(result is MasterDeletionService.Result.Deleted)
    assertNull(masterRepository.findByIdOrNull(master.id))
    assertNull(masterRepository.findByName(master.name))
  }

  @Test
  fun `deletes the owned player bestias along with the master`() {
    val master = createThrowawayMaster()
    // Created but not spawned: a bestia that is live in the world is exactly the case the IN_USE denial
    // exists for, so spawning it would refuse the deletion instead of exercising the cascade.
    playerBestiaFactory.create(
      master.id,
      PlayerBestiaFactory.PlayerBestiaCreateData(bestiaIdentifier = "blob", spawnPosition = Vec3L.ZERO)
    )
    assertEquals(1, playerBestiaRepository.findAllByMasterId(master.id).size)

    masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)

    assertTrue(playerBestiaRepository.findAllByMasterId(master.id).isEmpty())
  }

  @Test
  fun `deletes the unique items the master was carrying`() {
    val master = createThrowawayMaster()
    val instancesBefore = itemInstanceRepository.count()

    // Shoes are EQUIP and therefore not stackable, so granting a pair mints an ItemInstance row instead of
    // growing a stack. That is the case the delete order has to get right: the container slot pointing at
    // the instance must be gone before the instance itself can be removed.
    inventoryService.addItem(master, "shoes", 1)
    assertEquals(instancesBefore + 1, itemInstanceRepository.count())

    masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)

    assertEquals(instancesBefore, itemInstanceRepository.count())
  }

  @Test
  fun `disbands the party the deleted master owned`() {
    val master = createThrowawayMaster()
    // Party.owner is a non-null foreign key, so without the detach the delete would fail outright.
    val partyId = transactionTemplate.execute {
      val owner = masterRepository.findByIdOrThrow(master.id)
      val party = partyRepository.save(Party(owner = owner, name = "doomed-party-${master.id}"))
      masterRepository.save(owner)
      party.id
    }!!

    val result = masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)

    assertEquals(
      MasterDeletionService.Result.Deleted(master.name, PartyService.LeavePartyResult.Disbanded(partyId, emptyList())),
      result
    )
    assertNull(partyRepository.findByIdOrNull(partyId))
    assertNull(masterRepository.findByIdOrNull(master.id))
  }

  @Test
  fun `refuses a master belonging to another account and leaves it alone`() {
    val foreignMasterId = testFixture.account2.masterIds.first()
    val foreignMaster = masterRepository.findByIdOrThrow(foreignMasterId)

    val result = masterDeletionService.delete(ACCOUNT_ID, foreignMasterId, foreignMaster.name)

    assertEquals(MasterDeletionService.Result.Denied(MasterDeletionService.Denial.NOT_OWNED), result)
    assertNotNull(masterRepository.findByIdOrNull(foreignMasterId))
  }

  @Test
  fun `reports an unknown master as not owned, so ids cannot be probed for`() {
    val result = masterDeletionService.delete(ACCOUNT_ID, 999_999L, "whatever")

    assertEquals(MasterDeletionService.Result.Denied(MasterDeletionService.Denial.NOT_OWNED), result)
  }

  @Test
  fun `refuses a confirmation name that does not match and leaves the master alone`() {
    val master = createThrowawayMaster()

    val result = masterDeletionService.delete(ACCOUNT_ID, master.id, master.name + "-typo")

    assertEquals(MasterDeletionService.Result.Denied(MasterDeletionService.Denial.NAME_MISMATCH), result)
    assertNotNull(masterRepository.findByIdOrNull(master.id))

    masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)
  }

  /** Named uniquely per call because the master name carries a unique index. */
  private fun createThrowawayMaster(): Master {
    return masterFactory.create(
      ACCOUNT_ID,
      MasterFactory.CreateMasterData(
        name = "doomed${NEXT_NAME.getAndIncrement()}",
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
    /** Account 3 of the fixture: it starts with a single master, so there are free slots to create into. */
    const val ACCOUNT_ID = 3L
    val NEXT_NAME = AtomicInteger(1)
  }
}
