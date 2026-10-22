package net.bestia.zone.scenarios

import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.account.persistence.Account
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.master.MasterErrorSMSG
import net.bestia.zone.extensions.test
import net.bestia.zone.master.AvailableMasterSMSG
import net.bestia.zone.master.net.CreateMasterCMSG
import net.bestia.zone.master.MasterCreatedSMSG
import net.bestia.zone.master.net.GetMasterCMSG
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.master.net.SelectMasterCMSG
import net.bestia.zone.master.status.StatusAttribute
import net.bestia.zone.master.status.effortValues
import net.bestia.zone.dialog.DialogSMSG
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.world.persistence.MasterSpawnPointRepository
import net.bestia.zone.world.ArrivalScatter
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.persistence.findByIdOrThrow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.ArmorType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.master.bestia.GmBestiaGrant
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository

/**
 * Scenario: Spawns a blob bestia entity, connects, sends a kill message, and provides a placeholder for post-kill checks.
 */
class MasterCreateScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var masterSpawnPointRepository: MasterSpawnPointRepository

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: EcsWorld

  @Autowired
  private lateinit var worldView: WorldView

  @Autowired
  private lateinit var playerBestiaRepository: PlayerBestiaRepository

  @Autowired
  private lateinit var skillRepository: SkillRepository

  private lateinit var accountNoMaster: Account

  private lateinit var clientPlayerNoMaster: GameClientMock

  private var listedMasterId: Long = 0

  @BeforeAll
  fun setupMaster() {
    accountNoMaster = accountFactory.createAccount(4L)

    clientPlayerNoMaster = gameClientFactory.getGameClient(
      accountId = accountNoMaster.id,
    )
  }

  @AfterAll
  fun teardownMaster() {
    clientPlayerNoMaster.disconnect()
  }

  @BeforeEach
  fun beforeClearMessages() {
    clientPlayerNoMaster.clearMessages()
  }

  /**
   * Creating a master requires a spawn point, so every test that only cares about the other fields still
   * has to name one.
   */
  private fun anySpawnPointId(): Int = masterSpawnPointService.ensureComputed().first().id.toInt()

  @Test
  @Order(1)
  fun `listing master for a new account returns an empty list`() {
    clientPlayerNoMaster.sendMessage(GetMasterCMSG(clientPlayerNoMaster.connectedPlayerId))

    await {
      val masterList = clientPlayerNoMaster.getLastReceived(AvailableMasterSMSG::class)

      assertEquals(0, masterList.master.size)
    }
  }

  @Test
  @Order(2)
  fun `creating a master works`() {
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r", anySpawnPointId())
    )

    await {
      // Creation is acknowledged with a success message only; the created master's data is
      // verified via an explicit GetMaster in the next test (mirroring the real client flow).
      clientPlayerNoMaster.getLastReceived(MasterCreatedSMSG::class)
    }
  }

  @Test
  @Order(3)
  fun `listing master again returns the newly created master`() {
    clientPlayerNoMaster.sendMessage(GetMasterCMSG(clientPlayerNoMaster.connectedPlayerId))

    await {
      val masterList = clientPlayerNoMaster.getLastReceived(AvailableMasterSMSG::class)

      assertEquals(1, masterList.master.size)

      val master = masterList.master.first()
      assertEquals("mast0r", master.name)
      assertEquals(Color.BLUE, master.hairColor)
      assertEquals(Color.BLUE, master.skinColor)
      assertEquals(Hairstyle.HAIR_1, master.hair)
      assertEquals(Face.FACE_1, master.face)
      assertEquals(BodyType.BODY_M_1, master.body)
      assertEquals(0, master.slot)

      listedMasterId = master.id
    }
  }

  @Test
  @Order(4)
  fun `selecting the newly created master greets the player and consumes the intro marker`() {
    clientPlayerNoMaster.sendMessage(
      SelectMasterCMSG(
        clientPlayerNoMaster.connectedPlayerId,
        listedMasterId
      )
    )

    // MasterFactory seeded the marker at creation, MasterEntitySpawner replayed it out of the DB, and
    // the first status value recalc fired the greeting. The marker itself is short lived by design -
    // asserting on the dialog rather than on the live effect is what keeps this from racing the tick.
    await {
      assertNotNull(
        clientPlayerNoMaster.tryGetLastReceived(DialogSMSG::class),
        "materializing a freshly created master greets its owner"
      )
    }

    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayerNoMaster.connectedPlayerId)
    val marker = world.read {
      get(masterEntityId, StatusEffects::class)
        ?.activeEffects
        ?.firstOrNull { it.definitionId == StatusEffectId.MASTER_INTRO_MARKER.id }
    }

    assertNull(marker, "the marker removes itself once it has greeted, so it can never greet twice")
  }

  @Test
  @Order(5)
  fun `creating a master with an invalid name fails`() {
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r".repeat(10), anySpawnPointId())
    )

    await {
      val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

      assertEquals(MasterErrorSMSG.MasterErrorCode.INVALID_NAME, masterErrorSMSG.error)
    }
  }

  @Test
  @Order(5)
  fun `creating a master with same name fails`() {
    // Slot 0 holds the first mast0r, so a free slot is what lets the name be the thing that is refused.
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r", anySpawnPointId(), slot = 1)
    )

    await {
      val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

      assertEquals(MasterErrorSMSG.MasterErrorCode.NAME_ALREADY_TAKEN, masterErrorSMSG.error)
    }
  }

  @Test
  @Order(6)
  fun `creating more than the allowed master fails`() {
    // we have already created one master in slot 0 with this we effectively create maxMasters + 1. The last one
    // asks for a slot past the end, which is still reported as "too many": that check comes first.
    (1..Account.DEFAULT_MASTER_SLOT_COUNT).forEach { i ->
      clientPlayerNoMaster.sendMessage(
        CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0rNumber$i", anySpawnPointId(), slot = i)
      )

      if (i < Account.DEFAULT_MASTER_SLOT_COUNT) {
        val masterErrorSMSG = clientPlayerNoMaster.tryGetLastReceived(MasterErrorSMSG::class)
        assertNull(masterErrorSMSG)
      }

      if (i == Account.DEFAULT_MASTER_SLOT_COUNT) {
        await {
          val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

          assertEquals(MasterErrorSMSG.MasterErrorCode.MAX_MASTERS_REACHED, masterErrorSMSG.error)
        }
      }
    }
  }

  @Test
  @Order(7)
  fun `listing master also returns spawn point candidates ranked below the largest settlement`() {
    val account = accountFactory.createAccount(5L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(GetMasterCMSG(client.connectedPlayerId))

      await {
        val response = client.getLastReceived(AvailableMasterSMSG::class)

        assertTrue(response.spawnPoints.isNotEmpty(), "no spawn point candidates were offered")
        assertTrue(response.spawnPoints.size <= SettlementSpawnPoints.MAX_HOME_CANDIDATES)
        response.spawnPoints.forEach { candidate ->
          assertTrue(candidate.settlementName.isNotBlank())
          assertTrue(candidate.tier.isNotBlank())
          assertTrue(candidate.population > 0)
          assertTrue(candidate.biomeToken.startsWith("BIOME_"), "biome token was '${candidate.biomeToken}'")
          assertTrue(candidate.history.isNotEmpty(), "a home on offer has to have been founded")
        }
      }
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(8)
  fun `creating a master with a chosen spawn point sets its position and home settlement`() {
    val account = accountFactory.createAccount(6L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(GetMasterCMSG(client.connectedPlayerId))

      var chosen: AvailableMasterSMSG.SpawnPointCandidate? = null
      await {
        val response = client.getLastReceived(AvailableMasterSMSG::class)
        assertTrue(response.spawnPoints.isNotEmpty())
        chosen = response.spawnPoints.first()
      }
      val candidate = chosen!!

      client.clearMessages()
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "spawnpicker", spawnPointId = candidate.id)
      )

      await {
        client.getLastReceived(MasterCreatedSMSG::class)
      }

      val master = masterRepository.findByName("spawnpicker")!!
      val spawnPoint = masterSpawnPointRepository.findByIdOrThrow(candidate.id.toLong())

      assertEquals(candidate.settlementName, master.homeSettlementName)
      assertEquals(spawnPoint.position, master.spawnPosition)
      assertTrue(abs(master.currentPosition.x - spawnPoint.position.x) <= ArrivalScatter.RADIUS_TILES)
      assertTrue(abs(master.currentPosition.y - spawnPoint.position.y) <= ArrivalScatter.RADIUS_TILES)
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(9)
  fun `creating a master with an invalid spawn point id fails`() {
    val account = accountFactory.createAccount(7L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "badspawn", spawnPointId = Int.MAX_VALUE)
      )

      await {
        val masterErrorSMSG = client.getLastReceived(MasterErrorSMSG::class)

        assertEquals(MasterErrorSMSG.MasterErrorCode.INVALID_SPAWN_POINT, masterErrorSMSG.error)
      }
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(10)
  fun `creating a master without naming a spawn point fails`() {
    val account = accountFactory.createAccount(8L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      // 0 is what a client sends for the unset presence-less wire field - there is no world default to
      // fall back to, so it has to be refused like any other unknown id.
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "nospawn", spawnPointId = 0)
      )

      await {
        val masterErrorSMSG = client.getLastReceived(MasterErrorSMSG::class)

        assertEquals(MasterErrorSMSG.MasterErrorCode.INVALID_SPAWN_POINT, masterErrorSMSG.error)
      }

      assertNull(masterRepository.findByName("nospawn"), "no master must have been written")
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(11)
  fun `the chosen effort value distribution is persisted onto the master`() {
    val account = accountFactory.createAccount(9L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      // A lopsided but legal build: five attributes at the floor leave exactly enough for STR at 22.
      // Deliberately not the even spread, so a factory that ignored the message and fell back to the
      // default would fail here.
      val specialist = mapOf(
        StatusAttribute.STRENGTH to 22,
        StatusAttribute.AGILITY to 1,
        StatusAttribute.VITALITY to 1,
        StatusAttribute.INTELLIGENCE to 1,
        StatusAttribute.DEXTERITY to 1,
        StatusAttribute.WILLPOWER to 1
      )

      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "bruiser", anySpawnPointId(), specialist)
      )

      await { client.getLastReceived(MasterCreatedSMSG::class) }

      val master = masterRepository.findByName("bruiser")!!
      assertEquals(specialist, master.effortValues())
      // The whole budget went into the build, so nothing is left over to spend in-game.
      assertEquals(0, master.statusPoints)
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(12)
  fun `an effort value distribution that does not spend the budget exactly is refused`() {
    val account = accountFactory.createAccount(10L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      val underspent = StatusAttribute.entries.associateWith { 1 }
      val overspent = StatusAttribute.entries.associateWith { 10 }
      val belowFloor = StatusAttribute.entries.associateWith { 0 }

      // Reported as GENERAL_ERROR rather than a dedicated code: the creation screen keeps Create
      // disabled until the distribution is valid, so only a broken client can send these.
      mapOf(
        "underspent" to underspent,
        "overspent" to overspent,
        "belowfloor" to belowFloor
      ).forEach { (name, distribution) ->
        client.clearMessages()
        client.sendMessage(
          CreateMasterCMSG.test(client.connectedPlayerId, name, anySpawnPointId(), distribution)
        )

        await {
          val error = client.getLastReceived(MasterErrorSMSG::class)
          assertEquals(MasterErrorSMSG.MasterErrorCode.GENERAL_ERROR, error.error, "$name must be refused")
        }

        assertNull(masterRepository.findByName(name), "no master must have been written for $name")
      }
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(13)
  fun `a master created on a GM account owns the GM bestia with its level, stats, attacks and gear`() {
    val account = accountFactory.createAccount(11L)
    val client = gameClientFactory.getGameClient(accountId = account.id)
    var masterId = 0L

    try {
      // Connecting is what puts the account's authorities into the session the grant reads them from.
      client.connect()
      client.sendMessage(CreateMasterCMSG.test(client.connectedPlayerId, "gmowner", anySpawnPointId()))
      await { client.getLastReceived(MasterCreatedSMSG::class) }

      masterId = masterRepository.findByName("gmowner")!!.id
      val owned = playerBestiaRepository.findAllByMasterId(masterId).single()
      assertEquals(GmBestiaGrant.SPECIES, owned.bestia.identifier)
      assertEquals(owned.bestia.level, owned.level)

      // The bestia enters the world with the master, as any bestia the master already owns does.
      client.sendMessage(SelectMasterCMSG(client.connectedPlayerId, masterId))
      await { assertEquals(1, bestiaEntitiesOf(masterId).size) }
      val entityId = bestiaEntitiesOf(masterId).single().entityId

      val bestiaSkillIds = skillRepository.findAll().map { it.id }.filter { it in BESTIA_SKILL_IDS }
      worldView.read {
        assertEquals(owned.bestia.level, get(entityId, Level::class)!!.level)

        val skills = get(entityId, KnownSkills::class)!!
        bestiaSkillIds.forEach { assertTrue(skills.levelOf(it) > 0, "the GM bestia does not know skill $it") }

        val base = get(entityId, BaseStatusValues::class)!!
        assertEquals(
          listOf(owned.bestia.strength, owned.bestia.intelligence, owned.bestia.vitality, owned.bestia.dexterity, owned.bestia.willpower, owned.bestia.agility),
          listOf(base.strength, base.intelligence, base.vitality, base.dexterity, base.willpower, base.agility)
        )

        val equipment = get(entityId, Equipment::class)!!
        EquipmentSlot.entries.forEach { assertTrue(equipment.isSlotAvailable(it), "no $it slot") }
        ArmorType.entries.forEach { assertTrue(equipment.canWearArmorType(it), "cannot wear $it") }
      }
    } finally {
      client.disconnect()
      // A bestia outlives its owner's session, and a later master may reuse this master's id.
      bestiaEntitiesOf(masterId).forEach { bestia -> worldView.modify(bestia.entityId) { id -> destroy(id) } }
    }
  }

  @Test
  @Order(14)
  fun `a master created on an ordinary account owns no bestia`() {
    val account = accountFactory.createAccount(12L)
    val client = gameClientFactory.getGameClient(accountId = account.id, authorities = emptySet())

    try {
      client.connect()
      client.sendMessage(CreateMasterCMSG.test(client.connectedPlayerId, "plainowner", anySpawnPointId()))
      await { client.getLastReceived(MasterCreatedSMSG::class) }

      val master = masterRepository.findByName("plainowner")!!

      assertTrue(playerBestiaRepository.findAllByMasterId(master.id).isEmpty())
    } finally {
      client.disconnect()
    }
  }

  /**
   * The reported bug: a master created into the second slot of an empty account turned up first in the list.
   * Made out of order on purpose, so neither creation order nor the set the account keeps them in can pass this.
   */
  @Test
  @Order(15)
  fun `masters are listed in the slot they were created in, not in the order they were made`() {
    val account = accountFactory.createAccount(13L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      listOf("slotlast" to 2, "slotfirst" to 0, "slotmiddle" to 1).forEach { (name, slot) ->
        client.clearMessages()
        client.sendMessage(CreateMasterCMSG.test(client.connectedPlayerId, name, anySpawnPointId(), slot = slot))
        await { client.getLastReceived(MasterCreatedSMSG::class) }
      }

      client.clearMessages()
      client.sendMessage(GetMasterCMSG(client.connectedPlayerId))

      await {
        val masters = client.getLastReceived(AvailableMasterSMSG::class).master

        assertEquals(listOf("slotfirst" to 0, "slotmiddle" to 1, "slotlast" to 2), masters.map { it.name to it.slot })
      }
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(16)
  fun `creating a master in a slot that already holds one fails`() {
    val account = accountFactory.createAccount(14L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(CreateMasterCMSG.test(client.connectedPlayerId, "slotowner", anySpawnPointId(), slot = 1))
      await { client.getLastReceived(MasterCreatedSMSG::class) }

      client.clearMessages()
      client.sendMessage(CreateMasterCMSG.test(client.connectedPlayerId, "slotthief", anySpawnPointId(), slot = 1))

      await {
        val error = client.getLastReceived(MasterErrorSMSG::class)

        assertEquals(MasterErrorSMSG.MasterErrorCode.GENERAL_ERROR, error.error)
      }

      assertNull(masterRepository.findByName("slotthief"), "the taken slot must not be given to a second master")
      assertEquals(1, masterRepository.findByName("slotowner")!!.slot)
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(17)
  fun `creating a master in a slot the account does not have fails`() {
    val account = accountFactory.createAccount(15L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "slotbeyond", anySpawnPointId(), slot = Account.DEFAULT_MASTER_SLOT_COUNT)
      )

      await {
        val error = client.getLastReceived(MasterErrorSMSG::class)

        assertEquals(MasterErrorSMSG.MasterErrorCode.GENERAL_ERROR, error.error)
      }

      assertNull(masterRepository.findByName("slotbeyond"), "no master must have been written")
    } finally {
      client.disconnect()
    }
  }

  private fun bestiaEntitiesOf(masterId: Long): Set<ConnectionInfoService.PlayerEntity> {
    return worldView.read { OwnedBestia.ownedBy(this, masterId) }
  }

  private companion object {
    /** `skills.yml` reserves 1000 up to the guild skills at 9000 for the skills of bestia species. */
    val BESTIA_SKILL_IDS = 1000L until 9000L
  }
}




