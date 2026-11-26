package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.WorldRebirth
import org.springframework.stereotype.Component

/** Keeps players out of the game while a new world is being born, with a reason the client can show. */
@Component
class WorldRebirthGate(
  private val rebirth: WorldRebirth,
) {

  /**
   * Throws while a new world is being born. When Valkey cannot say, the player is let in: the zone then refuses them
   * itself if it is not ready, and an outage of Valkey must not lock everyone out.
   */
  fun requireWorld() {
    val underway = try {
      rebirth.isUnderway()
    } catch (e: Exception) {
      LOG.warn { "Could not read the world state, letting the login through: ${e.message}" }
      false
    }

    if (underway) {
      throw GameLoginException(GameLoginReason.WORLD_REBIRTH, "a new world is being born")
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
