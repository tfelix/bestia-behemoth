package net.bestia.zone.townsfolk.domain.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.townsfolk.domain.TownsfolkDomain

/**
 * Walks the evening's lantern line, stopping a moment at each lantern.
 *
 * The lanterns light by the clock on every client whether anybody walks to them or not - there is no state to
 * change - so this is the person rather than the mechanism: somebody seen going down the road at dusk, stopping
 * at each post. The round ends at the last lantern, a few kilometres out, and going home is [TownsfolkDomain.Goals.GO_HOME]'s.
 */
class TendLanternsActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "tendLanterns"

  override fun ground(state: WorldState): List<Action> {
    val route = state.get(TownsfolkDomain.LANTERN_ROUTE)?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val today = state.get(TownsfolkDomain.DAY_INDEX) ?: return emptyList()

    return listOf(
      Action(
        name = "tendLanterns",
        effects = listOf(
          Effects.set(TownsfolkDomain.LANTERNS_TENDED_ON_DAY, today),
          Effects.set(TownsfolkDomain.POSITION, route.last()),
        ),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(route.first()) ?: Long.MAX_VALUE).toFloat() },
        behavior = { WalkRoute(route, locomotion) },
      )
    )
  }
}

/**
 * Walks to each point of [route] in turn and stands at it for a moment.
 *
 * A leaf with a cursor rather than a sequence of `MoveTo`s, and it has to be: a sequence re-ticks from its first
 * child, so on reaching the second lantern the first `MoveTo` would find itself out of reach again and turn the
 * walker round. A lantern it cannot reach is skipped rather than failing the round.
 */
class WalkRoute(
  private val route: List<Vec3L>,
  private val locomotion: Locomotion,
  private val pauseSeconds: Float = PAUSE_SECONDS,
) : BtNode {

  private var index = 0
  private var paused = 0f

  override fun tick(context: BtContext): Status {
    while (index < route.size) {
      val target = route[index]
      if (locomotion.distanceTo(context.world, context.entityId, target) <= ARRIVAL_RADIUS) {
        paused += context.deltaTime
        if (paused < pauseSeconds) return Status.RUNNING
        paused = 0f
        index++
        continue
      }
      if (locomotion.stepToward(context, target)) return Status.RUNNING
      index++
    }
    return Status.SUCCESS
  }

  override fun toString(): String = "WalkRoute(${index}/${route.size})"

  private companion object {
    const val ARRIVAL_RADIUS = 2L
    const val PAUSE_SECONDS = 2.5f
  }
}
