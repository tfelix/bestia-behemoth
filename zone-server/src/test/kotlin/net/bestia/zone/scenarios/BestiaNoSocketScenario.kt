package net.bestia.zone.scenarios

import net.bestia.zone.engine.NoSqlOnTickExtension
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.world.net.ChunkRequestCMSG
import net.bestia.zone.world.stream.ChunkManifestSMSG
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
  }

  private companion object {
    /** Enough passes to walk the request budget through a view volume; the loop stops early once served. */
    const val REQUEST_ROUNDS = 40
  }
}