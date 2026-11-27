package net.bestia.login.scenario

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.bus.contract.LastWorld
import net.bestia.bus.valkey.ValkeyWorldRebirth
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.containers.GenericContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** While the zone gives birth to a new world, the game cannot start, and says why. The player's session survives. */
class WorldRebirthScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var valkeyContainer: GenericContainer<*>

  @Test
  fun `a stored session waits out the rebirth and still works afterwards`() {
    val registration = register(VirtualAuthenticator())
    val tokens = exchangeFully(registration.code, registration.verifier)

    duringRebirth {
      val refused = rawRefresh(tokens.refreshToken)

      assertEquals(503, refused.statusCode.value())
      assertTrue(refused.body!!.contains("WORLD_REBIRTH")) { "the client must tell this apart: ${refused.body}" }
    }

    refresh(tokens.refreshToken)
  }

  @Test
  fun `no browser login starts while a new world is born`() {
    duringRebirth {
      val refused = rawPost("/api/v1/auth/game/start", startBody("http://127.0.0.1:$LOOPBACK_PORT/callback", "s"))

      assertEquals(503, refused.statusCode.value())
    }
  }

  /** As the zone does it, with the zone's own Valkey user. */
  private fun duringRebirth(block: () -> Unit) {
    val client = RedisClient.create(
      RedisURI.Builder.redis(valkeyContainer.host, valkeyContainer.getMappedPort(6379))
        .withAuthentication("zone", "zone-dev-password".toCharArray())
        .build()
    )
    val rebirth = ValkeyWorldRebirth(client)
    rebirth.begin(LastWorld("Genesis", "VOID_TEAR", "LOST", 300, 100, 2, 1_800_000_000_000))
    try {
      block()
    } finally {
      rebirth.end()
      rebirth.close()
      client.shutdown()
    }
  }
}
