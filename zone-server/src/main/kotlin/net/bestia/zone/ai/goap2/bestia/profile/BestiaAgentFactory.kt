package net.bestia.zone.ai.goap2.bestia.profile

import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.geometry.Vec3L

/**
 * Attaches a [BestiaAiProfile] to a fresh [Agent]: writes its tuning knobs as permanent memory facts
 * (read by goal availability/desired-state checks, e.g. [BestiaDomain.HUNGER_THRESHOLD]) and builds
 * its goal list / action resolver from the profile's declared ids, so a mob's numbers can be tuned
 * from YAML without touching the goal/action Kotlin code at all.
 */
object BestiaAgentFactory {

  fun create(
    profile: BestiaAiProfile,
    homePosition: Vec3L,
    memory: Blackboard = Blackboard(),
    teamMemory: Blackboard? = null,
  ): Agent {
    memory.set(BestiaDomain.HOME_POSITION, homePosition, Blackboard.PERMANENT)
    memory.set(BestiaDomain.WANDER_RADIUS, profile.wanderRadius, Blackboard.PERMANENT)
    memory.set(BestiaDomain.HUNGER_THRESHOLD, profile.hungerThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.TIREDNESS_THRESHOLD, profile.tirednessThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.MELEE_RANGE, profile.meleeRange, Blackboard.PERMANENT)

    val goalsByName = BestiaDomain.Goals.ALL.associateBy { it.name }
    val goals = profile.goals.mapNotNull { tuning -> goalsByName[tuning.name]?.withBasePriority(tuning.basePriority) }

    return Agent(
      name = profile.identifier,
      goals = goals,
      memory = memory,
      actionResolver = BestiaDomain.resolver(profile.actionIds, profile.attacks),
      teamMemory = teamMemory,
    )
  }
}
