package net.bestia.zone.ai.domain

import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.geometry.Vec3L

/**
 * How a domain turns a profile into a live agent: the half that needs collaborators.
 *
 * Separate from [AiDomainCatalogue] because the two are asked different questions at different times. This
 * one is asked once per spawn, by `AiAgentFactory`, and can only answer with a navigation service and both
 * attack pathways in hand. One interface carrying both would put those beans behind profile validation,
 * which happens before any of them exist.
 */
interface AiDomainRuntime {

  val catalogue: AiDomainCatalogue

  /** The appetites this domain's agents accumulate. See [net.bestia.zone.ai.ecs.AiDriveSystem]. */
  val drives: List<Drive>

  /** Writes the profile's tuning knobs into a fresh agent's memory, as the permanent facts they are. */
  fun attach(memory: Blackboard, profile: AiProfile, homePosition: Vec3L, config: AiConfig?)

  fun resolver(profile: AiProfile): ActionResolver

  /** When perception should clear [net.bestia.zone.ai.core.state.CommonKeys.RESTED] for this profile. */
  fun restingWindow(profile: AiProfile): RestingWindow
}
