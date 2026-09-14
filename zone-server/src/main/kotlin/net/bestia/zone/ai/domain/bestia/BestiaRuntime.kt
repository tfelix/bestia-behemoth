package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.domain.AiDomainRuntime
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationService
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Builds live creatures out of [BestiaDomain].
 *
 * This is where the collaborators the wild archetypes need are held, and the reason `AiAgentFactory` no
 * longer holds them: a factory that constructed one domain's templates itself would have to grow every
 * other domain's dependencies as well.
 */
@Service
class BestiaRuntime(
  navigation: NavigationService,
  private val skills: SkillExecutionService,
  private val attackExecution: AttackExecutionService,
  /** Where wandering draws from. Defaulted for the server; a test passes a seed to pin a walk. */
  random: Random = Random.Default,
) : AiDomainRuntime {

  /** One shared instance: it holds only stateless collaborators, so there is nothing per-agent about it. */
  private val locomotion = Locomotion(navigation, random)

  override val catalogue = BestiaDomain

  override val drives: List<Drive> = BestiaDomain.DRIVES

  /**
   * Tuning facts never decay, hence [Blackboard.PERMANENT]: a melee range that quietly expired after ten
   * minutes would silently fall back to the domain default and change how the creature fights.
   */
  override fun attach(memory: Blackboard, profile: AiProfile, homePosition: Vec3L, config: AiConfig?) {
    val tuning = profile.tuning
    memory.set(BestiaDomain.HOME_POSITION, homePosition, Blackboard.PERMANENT)
    memory.set(BestiaDomain.ACTIVITY_CYCLE, tuning.activityCycle, Blackboard.PERMANENT)
    memory.set(BestiaDomain.WANDER_RADIUS, tuning.wanderRadius, Blackboard.PERMANENT)
    memory.set(BestiaDomain.MELEE_RANGE, tuning.meleeRange, Blackboard.PERMANENT)
    memory.set(BestiaDomain.HUNGER_THRESHOLD, tuning.hungerThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.TIREDNESS_THRESHOLD, tuning.tirednessThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.RESTLESS_THRESHOLD, tuning.restlessThreshold, Blackboard.PERMANENT)

    // The player's one numeric knob overrides the archetype's, always clamped. Everything else about the
    // species is not theirs to change.
    memory.set(
      BestiaDomain.AGGRESSION,
      config?.sanitised()?.aggression ?: tuning.aggression,
      Blackboard.PERMANENT,
    )
  }

  override fun resolver(profile: AiProfile): ActionResolver {
    return BestiaDomain.resolver(
      profile.actionIds,
      BestiaDomain.Collaborators(locomotion, skills, attackExecution, profile.attacks)
    )
  }

  /** A species sleeps by its own cycle, so there is nothing individual to read out of [memory] here. */
  override fun restingWindow(profile: AiProfile, memory: Blackboard): RestingWindow {
    return BestiaDomain.restingWindow(profile.tuning.activityCycle)
  }
}
