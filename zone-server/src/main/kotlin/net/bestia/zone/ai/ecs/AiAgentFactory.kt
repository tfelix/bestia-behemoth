package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.AiDomainRuntime
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service

/**
 * Builds the [AiAgent] component for a freshly spawned creature: has the profile's domain write its tuning
 * knobs into memory as permanent facts, resolves its goal list and action resolver from the ids the profile
 * names, and joins it to its faction's shared blackboard.
 *
 * Because the knobs go into memory rather than onto fields of the component, goal availability and priority
 * read them the same way they read hunger or position — so a mob's numbers can be retuned from YAML without
 * touching any goal or action code, and there is exactly one place each number lives.
 *
 * What it does *not* know is any particular domain. Which goals exist, which templates a resolver is built
 * from and what a creature's appetites are all come from the [AiDomainRuntime] the profile names, so a
 * second kind of inhabitant is a new bean rather than a branch in here.
 */
@Service
class AiAgentFactory(
  runtimes: List<AiDomainRuntime>,
  private val sharedMemory: SharedMemoryService,
) {

  private val byDomain = runtimes.associateBy { it.catalogue.id }

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
    // `AiProfileRegistry` already refused any profile naming a domain that does not exist, so reaching this
    // means the runtime bean for a known domain is missing from the context rather than that a file is wrong.
    val runtime = byDomain[profile.domain]
      ?: throw IllegalStateException(
        "AI profile '${profile.identifier}' wants domain '${profile.domain}', but no AiDomainRuntime " +
          "for it is registered; present are ${byDomain.keys.sorted()}"
      )

    runtime.attach(memory, profile, homePosition, config)

    val goals = profile.goals
      // A stance narrows the archetype's goals, never widens them: it can switch off foraging, but it cannot
      // teach a creature to hunt if its species never could.
      .filter { config == null || it.name in config.stance.goalNames }
      .mapNotNull { tuning ->
        runtime.catalogue.goalsByName[tuning.name]?.let { goal ->
          tuning.basePriority?.let(goal::withBasePriority) ?: goal
        }
      }

    return AiAgent(
      profileId = profile.identifier,
      name = profile.identifier,
      goals = goals,
      actionResolver = runtime.resolver(profile),
      memory = memory,
      teamMemory = sharedMemory.teamBoard(profile.faction),
      drives = runtime.drives,
      restingWindow = runtime.restingWindow(profile),
    )
  }
}
