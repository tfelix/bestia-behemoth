package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.geometry.Vec3L

/**
 * Goes in through one's own front door, and stops existing.
 *
 * There are no interiors, so being inside is being gone: the entity is destroyed and [IndoorRegistry]
 * keeps the one line needed to bring the same person back out at the same door. That is the whole point
 * of the night - a town asleep should cost a map entry per person, not an entity, an agent and a place in
 * every AI wave.
 *
 * Grounds only for somebody with a real house. A person a GM dropped at a bare coordinate has no door to
 * go through, and `SleepAtHomeActionTemplate` covers them instead - the two never compete, because
 * exactly one of them grounds.
 */
class EnterHomeActionTemplate(private val indoors: IndoorRegistry) : ActionTemplate {
  override val id = "enterHome"

  override fun ground(state: WorldState): List<Action> {
    state.get(TownsfolkDomain.HOME_BUILDING) ?: return emptyList()
    val door = state.get(TownsfolkDomain.HOME_POSITION) ?: return emptyList()
    val until = TownsfolkDomain.restHoursOf(state.get(TownsfolkDomain.OCCUPATION))

    return listOf(
      Action(
        name = "enterHome",
        preconditions = listOf(Precondition { TownsfolkDomain.isAtHome(it) }),
        // The same claims sleeping makes, because the same thing happens: the night is spent and they are
        // rested at the end of it. Nothing reads them back - the agent goes with the entity - but the
        // planner has to be able to see that going inside is what satisfies the goal.
        effects = listOf(
          Effects.set(TownsfolkDomain.TIREDNESS, 5),
          Effects.set(TownsfolkDomain.RESTED, true),
          Effects.set(TownsfolkDomain.INDOORS, true),
        ),
        cost = { 2f },
        behavior = { GoIndoors(door, until, indoors) },
      )
    )
  }
}

/**
 * Records where somebody went and takes them off the map.
 *
 * FAILURE without a [Townsfolk] marker, because there would be nothing to write down and the entity would
 * be destroyed with no way to bring it back. Nothing that can reach this leaf lacks one - the template
 * needs a house, and only the settlement spawner gives anybody a house - but the two facts live in
 * different files and a silent vanishing is not the failure to have.
 */
class GoIndoors(
  private val door: Vec3L,
  private val until: HourWindow,
  private val indoors: IndoorRegistry,
) : BtNode {

  override fun tick(context: BtContext): Status {
    val who = context.world.get(context.entityId, Townsfolk::class) ?: return Status.FAILURE

    indoors.enter(who.identity, door, until)
    context.world.destroy(context.entityId)

    return Status.SUCCESS
  }

  override fun toString(): String = "GoIndoors(until $until)"
}
