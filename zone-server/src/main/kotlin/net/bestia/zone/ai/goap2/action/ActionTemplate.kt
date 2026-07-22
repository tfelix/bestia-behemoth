package net.bestia.zone.ai.goap2.action

import net.bestia.zone.ai.goap2.state.WorldState

/**
 * One reusable, independently testable source of grounded [Action]s — e.g. "attack, one per known
 * skill" or "walk to a remembered vegetation spot" — identified by [id] so a profile can pick which
 * templates apply to it (see `BestiaAiProfile.actionIds`).
 *
 * This is the OOP alternative to hand-rolling one [ActionResolver] closure that rebuilds the *entire*
 * action list from scratch on every planner call: a domain instead composes a handful of small
 * templates, each owning one concern, via [CompositeActionResolver].
 */
interface ActionTemplate {
  val id: String
  fun ground(state: WorldState): List<Action>
}

/** An [ActionResolver] built by unioning every one of [templates]' grounding for a state. */
class CompositeActionResolver(private val templates: List<ActionTemplate>) : ActionResolver {
  override fun getAvailableActions(state: WorldState): List<Action> =
    templates.flatMap { it.ground(state) }.filter { it.isApplicable(state) }
}
