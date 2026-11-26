package net.bestia.zone.scenarios

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.engine.NoSqlOnTickExtension
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.world.net.ChunkRequestCMSG
import net.bestia.zone.world.stream.ChunkManifestSMSG
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This does not setup the whole socket connection. Instead, we hook into the communication before everything
 * is translated into bnet protobuf messages so we can save us the hassle to translate all the time and keep
 * working with kotlin native classes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NoSqlOnTickExtension::class)
@SpringBootTest
@ActiveProfiles("no-socket", "test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class BestiaNoSocketScenario(
  private val autoClientConnect: Boolean = true,
  private val clearMessagesBetweenTests: Boolean = true
) {

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  protected lateinit var clientPlayer1: GameClientMock
  protected lateinit var clientPlayer2: GameClientMock
  protected lateinit var clientPlayer3: GameClientMock

  protected val testData: ScenarioDataSetup.TestFixture by lazy {
    applicationContext.getBean(ScenarioDataSetup.TestFixture::class.java)
  }

  @BeforeAll
  fun setup() {
    clientPlayer1 = gameClientFactory.getGameClient(
      accountId = testData.account1.account.id,
    )
    clientPlayer2 = gameClientFactory.getGameClient(
      accountId = testData.account2.account.id,
    )
    clientPlayer3 = gameClientFactory.getGameClient(
      accountId = testData.account3.account.id,
    )

    if (autoClientConnect) {
      clientPlayer1.connect(testData.account1.masterIds.first())
      clientPlayer2.connect(testData.account2.masterIds.first())
      clientPlayer3.connect(testData.account3.masterIds.first())
    }
  }

  @AfterAll
  fun teardown() {
    clientPlayer1.disconnect()
    clientPlayer2.disconnect()
    clientPlayer3.disconnect()
  }

  @BeforeEach
  fun cleanup() {
    if (clearMessagesBetweenTests) {
      clientPlayer1.clearMessages()
      clientPlayer2.clearMessages()
      clientPlayer3.clearMessages()
    }
  }

  /** Retries on any exception: a message not received yet throws from `getLastReceived`, not an assertion. */
  protected fun await(fn: () -> Unit) {
    Awaitility.await().ignoreExceptions().atMost(GameClientMock.SERVER_TIMEOUT).untilAsserted {
      fn()
    }
  }

  /**
   * Gets terrain into the client's hands, because that is what visibility is keyed on: an entity is
   * announced to an account that holds the chunk it stands in, and nothing is held until the client has asked
   * for what the manifest offered and been served it.
   */
  protected fun holdTerrain(client: GameClientMock) {
    val subscriptions = applicationContext.getBean(ChunkSubscriptionService::class.java)

    await {
      assertTrue(
        client.tryGetLastReceived(ChunkManifestSMSG::class) != null,
        "the manifest is what authorises a request"
      )
    }

    repeat(REQUEST_ROUNDS) {
      val offered = client.getLastReceived(ChunkManifestSMSG::class).added.map { it.chunk }
      if (offered.isEmpty()) return@repeat

      client.sendMessage(ChunkRequestCMSG(client.connectedPlayerId, offered))
      await { assertTrue(subscriptions.sentTo(client.connectedPlayerId).isNotEmpty()) }
    }

    holdGroundAroundMaster(client)
  }

  /**
   * A master may stand at the edge of its chunk, and a scenario puts things a few metres from it. Later manifests
   * offer the chunks next to it, which the loop above may never see, so this asks until all of them are served.
   */
  private fun holdGroundAroundMaster(client: GameClientMock) {
    val world = applicationContext.getBean(WorldView::class.java)
    val subscriptions = applicationContext.getBean(ChunkSubscriptionService::class.java)
    val accountId = client.connectedPlayerId
    val asked = HashSet<ChunkPos>()

    await {
      // Read under a lease, because the subscriptions belong to the tick thread.
      val (ground, held) = world.read { groundAround(accountId) to subscriptions.sentTo(accountId).toSet() }
      val offered = ground.values.flatten()

      val unasked = offered - asked
      if (unasked.isNotEmpty()) {
        client.sendMessage(ChunkRequestCMSG(accountId, unasked))
        asked += unasked
      }

      assertTrue(ground.values.none { it.isEmpty() }, "every column around the master is offered")
      assertTrue(held.containsAll(offered), "everything offered around the master is served")
    }
  }

  /** The chunks offered to [accountId] in its master's chunk column and the eight around it, by column. */
  private fun groundAround(accountId: Long): Map<ChunkPos, List<ChunkPos>> {
    val subscriptions = applicationContext.getBean(ChunkSubscriptionService::class.java)
    val chunks = applicationContext.getBean(ChunkService::class.java)
    val anchor = assertNotNull(subscriptions.anchorOf(accountId), "the master stands in the world")
    val offered = subscriptions.announcedTo(accountId).groupBy { it.copy(z = 0) }

    return NEIGHBOUR_OFFSETS.associate { (dx, dy) ->
      val column = chunks.normalise(ChunkPos(anchor.x + dx, anchor.y + dy))
      column to offered[column].orEmpty()
    }
  }

  private companion object {
    /** Enough passes to walk the request budget through a view volume; the loop stops early once served. */
    const val REQUEST_ROUNDS = 40

    val NEIGHBOUR_OFFSETS = (-1..1).flatMap { dx -> (-1..1).map { dy -> dx to dy } }
  }
}