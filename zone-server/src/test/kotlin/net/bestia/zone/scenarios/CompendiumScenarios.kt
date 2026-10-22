package net.bestia.zone.scenarios

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.compendium.CompendiumRecorder
import net.bestia.zone.compendium.CompendiumSMSG
import net.bestia.zone.compendium.CompendiumStore
import net.bestia.zone.compendium.net.RequestCompendiumCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Player1 owns blobs from the fixture, so its book starts with them caught. Player2 owns none: it walks up to a
 * wild blob, then catches one.
 */
class CompendiumScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var catalogue: BestiaCatalogue

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var recorder: CompendiumRecorder

  @Autowired
  private lateinit var store: CompendiumStore

  private val player2MasterId by lazy { testData.account2.masterIds.first() }

  private var wildBlob: EntityId? = null

  @AfterAll
  fun forgetEverything() {
    // The Spring context is shared with every other scenario.
    wildBlob?.let { id -> world.modify(id) { destroy(it) } }
    store.deleteAllOf(testData.account1.masterIds.first())
    store.deleteAllOf(player2MasterId)
  }

  @Test
  @Order(1)
  fun `species a master already owns start out caught`() {
    clientPlayer1.sendMessage(RequestCompendiumCMSG(clientPlayer1.connectedPlayerId))

    await { assertEquals(true, blobEntryOf(clientPlayer1)?.caught) }
  }

  @Test
  @Order(2)
  fun `a wild bestia in sight is recorded and pushed`() {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)
    val masterAt = world.read { getOrThrow(masterEntityId, Position::class).toVec3L() }
    // Loads the learnsets now: the spawner reads them inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    wildBlob = world.read { spawner.spawnMob(this, BLOB, masterAt + Vec3L(4, 0, 0), persistent = false) }

    await { assertEquals(false, blobEntryOf(clientPlayer2)?.caught) }
  }

  @Test
  @Order(3)
  fun `a catch marks the species as caught`() {
    recorder.caught(clientPlayer2.connectedPlayerId, player2MasterId, BLOB)

    await { assertEquals(true, blobEntryOf(clientPlayer2)?.caught) }
  }

  private fun blobEntryOf(client: GameClientMock): CompendiumSMSG.Entry? {
    return client.tryGetLastReceived(CompendiumSMSG::class)?.entries?.singleOrNull { it.speciesId == BLOB }
  }

  private companion object {
    const val BLOB = 1L
  }
}
