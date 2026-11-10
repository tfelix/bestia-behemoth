package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.bt.sequence
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.OpenStation
import net.bestia.zone.ai.domain.bestia.StationClaims
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.world.anchor.SimulationAnchor

/**
 * Takes a station that waits for a free crafter and walks to it, grounding [BestiaDomain.Goals.CRAFT]. From then on
 * the station is its home and its work, as if its master had picked it there.
 */
class ClaimStationActionTemplate(
  private val locomotion: Locomotion,
  private val stations: StationClaims,
) : ActionTemplate {
  override val id = "claimStation"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    if (state.get(BestiaDomain.AT_WORK) == true) return emptyList()

    return state.get(BestiaDomain.OPEN_STATIONS).orEmpty().map { station ->
      Action(
        name = "claimStation(${station.structureId})",
        effects = listOf(Effects.set(BestiaDomain.AT_WORK, true)),
        cost = { station.position.distance(position).toFloat() + 1f },
        // Claimed before the walk, so two crafters never set off for the same station.
        behavior = {
          sequence {
            +Claim(station, stations)
            +MoveTo(station.position, locomotion, arrivalRadius = REACH)
          }
        },
      )
    }
  }

  /** Once only: the sequence ticks it again during the walk, when the station is already its own. */
  private class Claim(
    private val station: OpenStation,
    private val stations: StationClaims,
  ) : BtNode {
    private var claimed = false

    override fun tick(context: BtContext): Status {
      if (claimed) return Status.SUCCESS

      if (!stations.claim(context.world, context.entityId, station.structureId)) {
        // Taken by another crafter, or done: look past it until the next look around.
        context.memory.get(BestiaDomain.OPEN_STATIONS)?.let { open ->
          context.memory.set(BestiaDomain.OPEN_STATIONS, open - station, BestiaDomain.OPEN_STATIONS.retain)
        }
        return Status.FAILURE
      }

      claimed = true
      return Status.SUCCESS
    }

    override fun toString(): String = "Claim(${station.structureId})"
  }

  companion object {
    /** Near enough to work, with a tile to spare for the station's own footprint. */
    private const val REACH = 2L

    val READS: ComponentClassSet = setOf(OwnedBestia::class)

    val WRITES: ComponentClassSet = setOf(WorkAssignment::class, SimulationAnchor::class)
  }
}
