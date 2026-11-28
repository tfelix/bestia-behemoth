package net.bestia.zone.scenarios

import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestiastorage.BestiaLedgerScript
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.etching.EtchingJobs
import net.bestia.zone.etching.persistence.EtchingJobRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One master locks two of their own Bestias into a Carving Table and etches them: one etching is called off, the other
 * runs out and sends the Essence Gem by post.
 */
class CarvingTableEtchingScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var playerBestias: PlayerBestiaRepository

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var storage: BestiaStorageService

  @Autowired
  private lateinit var etchings: EtchingJobs

  @Autowired
  private lateinit var recipes: RecipeRegistry

  /** By identifier: recipe ids are content and move when `recipes.yml` does. */
  private val etchAmethyst: Long get() = recipes.ofIdentifier("ETCH_AMETHYST")!!.id

  @Autowired
  private lateinit var jobs: EtchingJobRepository

  @Autowired
  private lateinit var parcels: ParcelRepository

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var transactions: TransactionTemplate

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private val bestias = mutableListOf<Long>()

  @BeforeAll
  fun createArtificer() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "carver",
        hairColor = Color.RED,
        skinColor = Color.RED,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    inventoryService.grantToMaster(masterId, BestiaLedgerScript.ITEM_ID, 1)
    inventoryService.grantToMaster(masterId, CUT_AMETHYST, 2)
    repeat(2) {
      bestias += playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO, level = 10)).id
    }

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    val runicEtching = assertNotNull(skills.findByIdentifier("M_RUNIC_ETCHING")).id
    world.modify(masterEntity()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(runicEtching, 1) }
  }

  /** Every Spring context shares one H2 database; see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    jobs.deleteAll()
  }

  @Test
  @Order(1)
  fun `an etching of a locked Bestia takes the gem and the craft duration of its level`() {
    bestias.forEach { storage.deposit(client.connectedPlayerId, it, TABLE_ID) }
    await { assertTrue(bestias.all { playerBestias.findByIdOrNull(it)!!.depositedAtStructureId == TABLE_ID }) }

    val before = Instant.now()
    bestias.forEach { bestia -> world.modify(masterEntity()) { id -> etchings.start(this, id, TABLE_ID, etchAmethyst, bestia) } }

    await { assertEquals(2, jobs.findAllByStructureId(TABLE_ID).size) }
    val job = jobs.findByIdOrNull(bestias[0])!!
    val seconds = (job.endsAt.toEpochMilli() - before.toEpochMilli()) / 1000.0
    assertEquals(1.2 * 10 * 10, seconds, 2.0)
    assertNull(world.read { get(masterEntity(), Inventory::class)!!.getItems().firstOrNull { it.itemId == CUT_AMETHYST } })
  }

  @Test
  @Order(2)
  fun `a called off etching sends the gem back by post`() {
    etchings.callOff(masterId, bestias[1])

    assertNull(jobs.findByIdOrNull(bestias[1]))
    assertTrue(parcelItems().any { it.first == CUT_AMETHYST }, "the gem comes back")
    assertEquals(TABLE_ID, playerBestias.findByIdOrNull(bestias[1])!!.depositedAtStructureId, "the Bestia stays locked in")
  }

  @Test
  @Order(3)
  fun `an etching whose time is up takes the Bestia and posts its Essence Gem to the owner`() {
    val job = jobs.findByIdOrNull(bestias[0])!!
    job.endsAt = Instant.EPOCH
    job.chance = 1f
    jobs.save(job)

    etchings.resolve(bestias[0])

    assertNull(playerBestias.findByIdOrNull(bestias[0]), "the Bestia became the essence")
    val blob = playerBestias.findByIdOrNull(bestias[1])!!.bestia.id
    assertTrue(parcelItems().any { it.second == Essence(blob, GemTier.AMETHYST) }, "the Essence Gem waits at the post")
  }

  /** Item id and held essence of everything waiting at the post for the master. */
  private fun parcelItems(): List<Pair<Long, Essence?>> {
    return transactions.execute {
      parcels.findByRecipientMasterIdOrderByLatestArrival(masterId).flatMap { parcel ->
        parcel.container.slots.map { slot ->
          (slot.itemInstance?.item?.id ?: slot.item?.id ?: 0L) to slot.itemInstance?.heldEssence
        }
      }
    }.orEmpty()
  }

  private fun masterEntity() = connectionInfoService.getSelectedMasterEntityId(client.connectedPlayerId)

  private companion object {
    const val LOGIN_ACCOUNT_ID = 62L
    const val TABLE_ID = 9100L
    const val CUT_AMETHYST = 136L
  }
}
