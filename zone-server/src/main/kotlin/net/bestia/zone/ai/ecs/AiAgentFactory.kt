package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationService
import org.springframework.stereotype.Service

/**
 * Builds the [AiAgent] component for a freshly spawned creature: writes the profile's tuning knobs into its
 * memory as permanent facts, resolves its goal list and action resolver from the ids the profile names, and
 * joins it to its faction's shared blackboard.
 *
 * Because the knobs go into memory rather than onto fields of the component, goal availability and priority
 * read them the same way they read hunger or position — so a mob's numbers can be retuned from YAML without
 * touching any goal or action code, and there is exactly one place each number lives.
 *
 * A Spring `@Service` rather than the object it replaced, because the action templates it builds now need
 * real collaborators: navigation to move and the skill service to attack.
 */
@Service
class AiAgentFactory(
  navigation: NavigationService,
  private val skills: SkillExecutionService,
  private val sharedMemory: SharedMemoryService,
) {

  /** One shared instance: it holds only the navigation service, so there is nothing per-agent about it. */
  private val locomotion = Locomotion(navigation)

  /**
   * Builds an agent for [profile]. [config] is the owning player's standing order, for a player-owned bestia;
   * a wild mob passes null and simply runs its archetype as authored.
   */
  fun create(
    profile: AiProfile,
    homePosition: Vec3L,
    config: AiConfig? = null,
    memory: Blackboard = Blackboard(),
  ): AiAgent {
    writeTuning(memory, profile, homePosition, config)

    val goals = profile.goals
      // A stance narrows the archetype's goals, never widens them: it can switch off foraging, but it cannot
      // teach a creature to hunt if its species never could.
      .filter { config == null || it.name in config.stance.goalNames }
      .mapNotNull { tuning ->
        BestiaDomain.Goals.BY_NAME[tuning.name]?.let { goal ->
          tuning.basePriority?.let(goal::withBasePriority) ?: goal
        }
      }

    return AiAgent(
      profileId = profile.identifier,
      name = profile.identifier,
      goals = goals,
      actionResolver = BestiaDomain.resolver(profile.actionIds, locomotion, skills, profile.attacks),
      memory = memory,
      teamMemory = sharedMemory.teamBoard(profile.faction),
    )
  }

  /**
   * Tuning facts never decay, hence [Blackboard.PERMANENT]: a melee range that quietly expired after ten
   * minutes would silently fall back to the domain default and change how the creature fights.
   */
  private fun writeTuning(memory: Blackboard, profile: AiProfile, homePosition: Vec3L, config: AiConfig?) {
    val tuning = profile.tuning
    memory.set(BestiaDomain.HOME_POSITION, homePosition, Blackboard.PERMANENT)
    memory.set(BestiaDomain.ACTIVITY_CYCLE, tuning.activityCycle, Blackboard.PERMANENT)
    memory.set(BestiaDomain.WANDER_RADIUS, tuning.wanderRadius, Blackboard.PERMANENT)
    memory.set(BestiaDomain.MELEE_RANGE, tuning.meleeRange, Blackboard.PERMANENT)
    memory.set(BestiaDomain.HUNGER_THRESHOLD, tuning.hungerThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.TIREDNESS_THRESHOLD, tuning.tirednessThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.RESTLESS_THRESHOLD, tuning.restlessThreshold, Blackboard.PERMANENT)

    // The player's two knobs override the archetype's, always clamped. Everything else about the species is
    // not theirs to change.
    val sanitised = config?.sanitised()
    memory.set(
      BestiaDomain.FLEE_THRESHOLD_PCT,
      sanitised?.fleeThresholdPct ?: tuning.fleeThresholdPct,
      Blackboard.PERMANENT,
    )
    memory.set(
      BestiaDomain.AGGRESSION,
      sanitised?.aggression ?: tuning.aggression,
      Blackboard.PERMANENT,
    )
  }
}
