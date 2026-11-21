package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A member's click on their guild's Empyrite opens the Stronghold window. Its Invest buttons click with a track, its
 * Hire button with the steward action, its watcher buttons with the watcher actions.
 */
@Component
class EmpyriteInteraction(
  private val window: StrongholdWindow,
) : PropInteraction {

  override val kind = StaticEntityKind.EMPYRITE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return window.open(world, actorId, propId)
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    return when (args.text(ScriptArgKeys.ACTION)) {
      INVEST -> invest(world, actorId, propId, args)
      HIRE_STEWARD -> window.hireSteward(world, actorId, propId)
      ADD_WATCHER -> changeWatchers(world, actorId, propId, args, +1)
      REMOVE_WATCHER -> changeWatchers(world, actorId, propId, args, -1)
      BRING_BACK_WATCHER -> window.bringBackWatcher(world, actorId, propId)
      else -> interact(world, actorId, propId)
    }
  }

  private fun changeWatchers(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs, delta: Int): OpError? {
    val kind = args.text(ScriptArgKeys.WATCHER)?.let { name -> WatcherKind.entries.firstOrNull { it.name == name } }
      ?: return OpError.REQUEST_REFUSED

    return window.changeWatchers(world, actorId, propId, kind, delta)
  }

  private fun invest(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    val track = args.text(ScriptArgKeys.TRACK)?.let { name -> StrongholdTrack.entries.firstOrNull { it.name == name } }
      ?: return OpError.REQUEST_REFUSED

    return window.invest(world, actorId, propId, track)
  }

  companion object {
    const val INVEST = "invest"
    const val HIRE_STEWARD = "hire_steward"
    const val ADD_WATCHER = "add_watcher"
    const val REMOVE_WATCHER = "remove_watcher"
    const val BRING_BACK_WATCHER = "bring_back_watcher"
  }
}
