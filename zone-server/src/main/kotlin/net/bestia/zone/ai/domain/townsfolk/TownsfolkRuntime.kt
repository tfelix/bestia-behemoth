package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.domain.AiDomainRuntime
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationService
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Builds townspeople out of [TownsfolkDomain].
 *
 * Needs only navigation, where the creatures' runtime needs both attack pathways as well - which is the
 * seam earning its keep: townsfolk do not fight, so nothing about them reaches the battle system.
 */
@Service
class TownsfolkRuntime(
  navigation: NavigationService,
  private val indoors: IndoorRegistry,
  private val work: SettlementWork,
  private val production: TownsfolkProduction,
  /** Where wandering draws from. Defaulted for the server; a test passes a seed to pin a walk. */
  random: Random = Random.Default,
) : AiDomainRuntime {

  private val locomotion = Locomotion(navigation, random)

  override val catalogue = TownsfolkDomain

  override val drives: List<Drive> = TownsfolkDomain.DRIVES

  /**
   * [config] is a player's standing order for a bestia they own and has no meaning here - nobody owns a
   * townsperson. It is ignored rather than rejected, because the caller is generic.
   */
  override fun attach(memory: Blackboard, profile: AiProfile, homePosition: Vec3L, config: AiConfig?) {
    val tuning = profile.tuning
    memory.set(TownsfolkDomain.HOME_POSITION, homePosition, Blackboard.PERMANENT)
    memory.set(TownsfolkDomain.WANDER_RADIUS, tuning.wanderRadius, Blackboard.PERMANENT)
    memory.set(TownsfolkDomain.TIREDNESS_THRESHOLD, tuning.tirednessThreshold, Blackboard.PERMANENT)
    memory.set(TownsfolkDomain.HUNGER_THRESHOLD, tuning.hungerThreshold, Blackboard.PERMANENT)
  }

  override fun resolver(profile: AiProfile): ActionResolver {
    return TownsfolkDomain.resolver(
      profile.actionIds,
      TownsfolkDomain.Collaborators(locomotion, indoors, work, production),
    )
  }

  /** The occupation's hours when the caller seeded one, and ordinary hours otherwise. */
  override fun restingWindow(profile: AiProfile, memory: Blackboard): RestingWindow {
    return TownsfolkDomain.restingWindowFor(memory.get(TownsfolkDomain.OCCUPATION))
  }
}
