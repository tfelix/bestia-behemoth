package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.battle.attack.BasicAttackResolver
import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.domain.AiDomainRuntime
import net.bestia.zone.ai.domain.bestia.action.ClaimStationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.CollectPropActionTemplate
import net.bestia.zone.ai.domain.bestia.action.FellTreeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.GatherWork
import net.bestia.zone.ai.domain.bestia.action.TakeFoodFromChestActionTemplate
import net.bestia.zone.ai.domain.bestia.action.ChestExchange
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.container.ItemHandover
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
  private val basicAttacks: BasicAttackResolver,
  private val care: BagCare,
  private val promotion: CombatTargetPromotion,
  private val aoi: EntityAOIService,
  private val handover: ItemHandover,
  private val stations: StationClaims,
  /** Where wandering draws from. Defaulted for the server; a test passes a seed to pin a walk. */
  random: Random = Random.Default,
) : AiDomainRuntime {

  /** One shared instance: it holds only stateless collaborators, so there is nothing per-agent about it. */
  private val locomotion = Locomotion(navigation, random)

  override val catalogue = BestiaDomain

  override val reads: ComponentClassSet =
    GatherWork.READS + FellTreeActionTemplate.READS + ChestExchange.READS + TakeFoodFromChestActionTemplate.READS +
      ClaimStationActionTemplate.READS

  override val writes: ComponentClassSet =
    CollectPropActionTemplate.WRITES + FellTreeActionTemplate.WRITES + ClaimStationActionTemplate.WRITES

  override val drives: List<Drive> = BestiaDomain.DRIVES

  /**
   * Tuning facts never decay, hence [Blackboard.PERMANENT]: an attack range that quietly expired after ten
   * minutes would silently fall back to the domain default and change how the creature fights.
   */
  override fun attach(memory: Blackboard, profile: AiProfile, homePosition: Vec3L, config: AiConfig?) {
    val tuning = profile.tuning
    memory.set(BestiaDomain.HOME_POSITION, homePosition, Blackboard.PERMANENT)
    memory.set(BestiaDomain.ACTIVITY_CYCLE, tuning.activityCycle, Blackboard.PERMANENT)
    memory.set(BestiaDomain.WANDER_RADIUS, tuning.wanderRadius, Blackboard.PERMANENT)
    memory.set(BestiaDomain.ATTACK_RANGE, attackRangeOf(profile), Blackboard.PERMANENT)
    memory.set(BestiaDomain.HUNGER_THRESHOLD, tuning.hungerThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.TIREDNESS_THRESHOLD, tuning.tirednessThreshold, Blackboard.PERMANENT)
    memory.set(BestiaDomain.RESTLESS_THRESHOLD, tuning.restlessThreshold, Blackboard.PERMANENT)

    // The player's courage step overrides the archetype's aggression. Everything else about the species is not
    // theirs to change.
    memory.set(BestiaDomain.AGGRESSION, config?.courage?.aggression ?: tuning.aggression, Blackboard.PERMANENT)

    if (config != null) attachOrders(memory, config, homePosition, tuning.wanderRadius)
  }

  /** The lines an owned bestia looks after itself and gets out of a fight at, and where it works. */
  private fun attachOrders(memory: Blackboard, config: AiConfig, homePosition: Vec3L, wanderRadius: Long) {
    memory.set(BestiaDomain.EAT_BELOW_STAMINA_PCT, BestiaDomain.DEFAULT_EAT_BELOW_STAMINA_PCT, Blackboard.PERMANENT)
    memory.set(BestiaDomain.HEAL_BELOW_HEALTH_PCT, BestiaDomain.DEFAULT_HEAL_BELOW_HEALTH_PCT, Blackboard.PERMANENT)
    config.courage.retreatBelowHealthPct?.let { memory.set(BestiaDomain.RETREAT_BELOW_HEALTH_PCT, it, Blackboard.PERMANENT) }
    memory.set(BestiaDomain.FLEES_ON_SIGHT, config.courage.fleesOnSight, Blackboard.PERMANENT)
    memory.set(BestiaDomain.TAKES_ON_STRONGER_FOES, config.courage.takesOnStrongerFoes, Blackboard.PERMANENT)
    if (config.job == BestiaJob.GATHER) {
      memory.set(BestiaDomain.GATHER_AREA, WorkArea(homePosition, wanderRadius), Blackboard.PERMANENT)
    }
    if (config.job == BestiaJob.CRAFT) {
      memory.set(BestiaDomain.CRAFT_AREA, WorkArea(homePosition, wanderRadius), Blackboard.PERMANENT)
    }
    if (config.job == BestiaJob.MINE) {
      memory.set(BestiaDomain.MINE_AREA, WorkArea(homePosition, wanderRadius), Blackboard.PERMANENT)
    }
    if (config.job == BestiaJob.GUARD) {
      memory.set(BestiaDomain.GUARD_AREA, WorkArea(homePosition, wanderRadius), Blackboard.PERMANENT)
    }
  }

  // A ranged species stops at shooting distance; one that also bites lets the target come to it.
  private fun attackRangeOf(profile: AiProfile): Long =
    profile.attacks.filterIsInstance<DefaultAttackDefinition>().maxOfOrNull { it.range }
      ?: BestiaDomain.DEFAULT_ATTACK_RANGE

  override fun resolver(profile: AiProfile): ActionResolver {
    return BestiaDomain.resolver(
      profile.actionIds,
      BestiaDomain.Collaborators(
        locomotion, skills, attackExecution, basicAttacks, care, promotion, aoi, handover, stations, profile.attacks
      )
    )
  }

  /** A species sleeps by its own cycle, so there is nothing individual to read out of [memory] here. */
  override fun restingWindow(profile: AiProfile, memory: Blackboard): RestingWindow {
    return BestiaDomain.restingWindow(profile.tuning.activityCycle)
  }
}
