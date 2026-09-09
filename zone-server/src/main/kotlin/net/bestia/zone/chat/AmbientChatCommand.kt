package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.ambient.AmbientSiteResolver
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import org.springframework.stereotype.Component

/**
 * Reports why the wilderness around the caller looks the way it does.
 *
 * "There is nothing here" has four causes with four different answers - a species nobody authored for this
 * biome, a town's keep-out ring, water, or the danger thinning doing its job - and they are indistinguishable
 * from inside the game. This prints the breakdown for the ground the caller is standing on, which turns a
 * session of walking around and guessing into one line.
 *
 * Logged rather than sent back: [ChatCommand] has no reply channel, and `/spawn` sets the precedent. The
 * figures go to the server log next to the ambient layer's own boot line.
 */
@Component
class AmbientChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val resolver: AmbientSiteResolver,
  private val config: AmbientSpawnConfig,
  private val world: WorldView
) : ChatCommand() {

  override fun getHelpText(): String {
    return "/ambient - Reports the ambient wilderness density around you, and why it is what it is."
  }

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun isMatch(cmdText: String): Boolean {
    return cmdText.trim() == "/ambient"
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    if (!config.enabled) {
      LOG.info { "Ambient spawn is disabled; the wilderness holds only what the dens produce" }
      return true
    }

    val entityId = connectionInfoService.getActiveEntityId(playerId)
    val position = world.read { get(entityId, Position::class) }?.toVec3L() ?: return false

    val tally = AmbientSiteResolver.Tally()
    val sites = resolver.tallyAround(position.x, position.y, WINDOW_TILES, tally)

    val windowKm2 = (WINDOW_TILES / 1_000.0) * (WINDOW_TILES / 1_000.0)
    val perKm2 = sites / windowKm2
    val onCamera = perKm2 * CAMERA_KM2

    LOG.info {
      "Ambient at ${position.x}/${position.y}: $sites site(s) in ${WINDOW_TILES}x$WINDOW_TILES tiles " +
          "= ${"%.0f".format(perKm2)}/km2, about ${"%.1f".format(onCamera)} on camera. $tally. " +
          "Memo holds ${resolver.memoSize} cell(s)."
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Wide enough to hold a few hundred sites, narrow enough to describe the ground underfoot. */
    const val WINDOW_TILES = 600L

    /** The camera footprint the density tests use; see `WildSpawnDensityTest`. */
    const val CAMERA_KM2 = 0.060 * 0.060
  }
}
