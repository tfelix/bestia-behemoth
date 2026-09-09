package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.world.stream.InterestRange
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Checks that the ambient wilderness is configured coherently, before anybody can walk into it.
 *
 * Nothing is placed here, unlike [WildSpawnerBootRunner]: an ambient site is a function of the world seed
 * evaluated near a player, so there is no boot-time population to build. What there is instead is a
 * cross-check that cannot live in `AmbientSpawnConfig`'s own `init`, because it needs values the config does
 * not have - the live [InterestRange], which is derived from the world's chunk size, and the widest wander
 * radius any AI profile declares.
 *
 * ### Why this is worth failing the boot over
 *
 * A creature is spawned when its *site* comes within `activation-radius-tiles`, and it then wanders up to
 * its own radius from that site. If the activation radius does not clear the interest radius plus that
 * wander radius, a creature can amble into a player's view from a site that was never stocked. The symptom
 * is a wilderness that flickers at its edges - which reads as a rendering or a streaming fault, and would
 * cost a long time to trace back to two numbers in a config file.
 */
@Component
@Order(106)
class AmbientSpawnerBootRunner(
  private val config: AmbientSpawnConfig,
  private val interestRange: InterestRange,
  private val profileRegistry: AiProfileRegistry
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (!config.enabled) {
      LOG.info { "Ambient spawn is disabled; the wilderness holds only what the dens produce" }
      return
    }

    val interestRadius = interestRange.cubeEdge / 2
    val wanderRadius = profileRegistry.all().maxOfOrNull { it.tuning.wanderRadius } ?: 0L
    val required = interestRadius + wanderRadius

    require(config.activationRadiusTiles >= required) {
      "ambient-spawn.activation-radius-tiles is ${config.activationRadiusTiles}, which does not clear the " +
          "interest radius ($interestRadius) plus the widest wander radius ($wanderRadius). A creature " +
          "could wander into view from a site that was never stocked; raise it to at least $required."
    }

    LOG.info {
      "Ambient spawn: one site per ${config.spacingTiles} tiles, stocked within " +
          "${config.activationRadiusTiles} of a player (needs >= $required), " +
          "${config.spawnsPerPass}/pass, throttle x${config.throttleFactor}"
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
