package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState
import net.bestia.zone.geometry.Vec3L
import kotlin.random.Random

/** Steps to a random tile within [BestiaDomain.WANDER_RADIUS] of home. See [BestiaDomain.fallbackWander]. */
class WanderActionTemplate(private val random: Random = Random.Default) : ActionTemplate {
  override val id = "wander"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(BestiaDomain.HOME_POSITION) ?: return emptyList()
    val radius = state.get(BestiaDomain.WANDER_RADIUS) ?: BestiaDomain.DEFAULT_WANDER_RADIUS
    val target = randomPointWithin(home, radius)

    return listOf(
      Action(
        name = "wanderTo($target)",
        effects = listOf(Effects.set(BestiaDomain.POSITION, target)),
        cost = { 5f },
      )
    )
  }

  private fun randomPointWithin(home: Vec3L, radius: Long): Vec3L {
    val span = (radius * 2 + 1).coerceAtLeast(1)
    val dx = random.nextLong(span) - radius
    val dy = random.nextLong(span) - radius
    return Vec3L(home.x + dx, home.y + dy, home.z)
  }
}
