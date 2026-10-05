package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * How much processing an agent gets: its [AiDetail], and how many times less often that tier is run.
 *
 * Most of a zone's creatures are ones no player is looking at - an interest cube holds around a hundred and
 * forty, the camera shows about sixty tiles - so running them all at full rate spends the tick on scenery.
 * Nothing is ever stopped, though: an unseen creature still eats, sleeps and walks, only less often.
 *
 * Anything without an [AiThrottleable] marker - den mobs, bosses, `/spawn`ed creatures, player bestias - never
 * drops below [AiDetail.REDUCED], and an AI profile may raise the floor further (`min_detail`).
 */
@Service
class AiThrottle(
  private val config: AmbientSpawnConfig,
  private val players: ActivePlayerAOIService,
  private val visibility: EntityVisibility,
) {

  fun detailOf(world: World, id: EntityId, agent: AiAgent): AiDetail {
    if (world.has(id, PlayerControlled::class)) return AiDetail.FULL

    // Aggro beats distance: a fight that walks out of view must not go into slow motion.
    if (agent.memory.get(CommonKeys.IS_AGGRO) == true) return AiDetail.FULL

    val position = world.get(id, Position::class) ?: return AiDetail.FULL
    val seen = when {
      players.anyWithinHorizontal(position.x, position.y, FULL_DETAIL_TILES) -> AiDetail.FULL
      visibility.observersOf(id).isNotEmpty() -> AiDetail.REDUCED
      else -> AiDetail.BACKGROUND
    }

    val floor = if (world.has(id, AiThrottleable::class)) {
      agent.minDetail
    } else {
      agent.minDetail.atLeast(AiDetail.REDUCED)
    }

    return seen.atLeast(floor)
  }

  /** How many times less often [agent]'s tier runs than full detail; 1 at full detail. */
  fun factorOf(agent: AiAgent): Int {
    return when (agent.detail) {
      AiDetail.FULL -> 1
      AiDetail.REDUCED -> config.throttleFactor
      AiDetail.BACKGROUND -> config.backgroundFactor
    }
  }

  private companion object {
    /**
     * Tiles within which a creature runs at full rate.
     *
     * Comfortably past what the camera shows - the spring arm reaches 36 m and the visible ground is on the
     * order of sixty tiles across - so a creature is already thinking normally before it can be looked at,
     * and the throttle is never something a player can see happening.
     */
    const val FULL_DETAIL_TILES = 96L
  }
}
