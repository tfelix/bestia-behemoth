package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Whether an agent may skip this round of thinking.
 *
 * ### Why the baseline population needs this at all
 *
 * At one creature every thirty tiles a single player's interest cube holds around a hundred and forty
 * agents, against the two or three a den layer produced. Perception is the expensive part - an area-of-
 * interest cube query plus component reads for every agent, twice a second - and almost all of that work is
 * spent on creatures the player cannot see: the cube reaches 176 tiles and the camera shows about sixty.
 *
 * ### What is never throttled
 *
 * Anything without an [AiThrottleable] marker, which is every den mob, every boss, every `/spawn`ed
 * creature and every player-owned bestia. Beyond that, a throttleable creature is still run at full rate
 * when a player is close enough to see it, and whenever it is angry - a creature that has been hit must
 * fight back at full speed however far from home the fight has wandered.
 *
 * Returns a *cadence* rather than a yes/no so a factor of 1 disables the whole mechanism without a code
 * change, which is what makes it safe to leave the knob in a config file.
 */
@Service
class AiThrottle(private val config: AmbientSpawnConfig) {

  /** False when the throttle is switched off, so a caller can skip gathering what it would need. */
  val isActive: Boolean get() = config.throttleFactor > 1

  /**
   * How many times longer than usual this agent may wait before thinking again.
   *
   * 1 means "no different from anything else".
   */
  fun factorFor(world: World, id: EntityId, agent: AiAgent, players: List<Vec3L>): Int {
    if (config.throttleFactor == 1) return 1
    if (!world.has(id, AiThrottleable::class)) return 1
    if (world.has(id, PlayerControlled::class)) return 1

    // Aggro beats distance: something that has been attacked is in a fight, and a fight that walks out of
    // view must not go into slow motion.
    if (agent.memory.get(CommonKeys.IS_AGGRO) == true) return 1

    val position = world.get(id, Position::class)?.toVec3L() ?: return 1
    for (player in players) {
      val dx = player.x - position.x
      val dy = player.y - position.y
      if (dx * dx + dy * dy <= FULL_FIDELITY_TILES * FULL_FIDELITY_TILES) return 1
    }

    return config.throttleFactor
  }

  private companion object {
    /**
     * Tiles within which a throttleable creature still runs at full rate.
     *
     * Comfortably past what the camera shows - the spring arm reaches 36 m and the visible ground is on the
     * order of sixty tiles across - so a creature is already thinking normally before it can be looked at,
     * and the throttle is never something a player can see happening.
     */
    const val FULL_FIDELITY_TILES = 96L
  }
}
