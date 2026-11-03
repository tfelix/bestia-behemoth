package net.bestia.zone.honor

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * One call per act that no feature commits yet: stealing, ward stones, commissions, helping townsfolk. The feature
 * that adds the act calls the matching method; until then `/honor act` commits them by hand.
 *
 * Every method takes the master entity that answers for the act, on the tick thread.
 */
@Component
class HonorEvents(
  private val honor: HonorService,
  private val statusEffects: StatusEffectService,
) {

  /**
   * @param ownerHonorableCitizen the level of the Trader skill Honorable Citizen of whoever was robbed, which
   *   makes the theft cost 20% more per level
   */
  fun stole(world: World, thief: EntityId, tier: StolenTier, at: Vec3L, ownerHonorableCitizen: Int = 0) {
    val recorded = honor.record(
      world, tier.act, thief, at, lossFactor = 1.0 + HONORABLE_CITIZEN_PER_LEVEL * ownerHonorableCitizen,
    )
    if (recorded.seen) {
      statusEffects.applyEffect(world, thief, StatusEffectId.THIEF, level = 1)
    }
  }

  fun returnedStolenItem(world: World, master: EntityId, at: Vec3L) {
    honor.record(world, HonorAct.RETURN_STOLEN_ITEM, master, at)
  }

  fun dispelledWardStone(world: World, master: EntityId, at: Vec3L) {
    honor.record(world, HonorAct.DISPEL_WARD_STONE, master, at)
  }

  fun destroyedWardStone(world: World, master: EntityId, at: Vec3L) {
    honor.record(world, HonorAct.DESTROY_WARD_STONE, master, at)
  }

  /** The broker who held the contract holds the grudge, wherever the master was when it lapsed. */
  fun abandonedCommission(world: World, master: EntityId, broker: Long) {
    honor.recordAgainst(world, HonorAct.ABANDON_COMMISSION, master, broker)
  }

  fun completedCommission(world: World, master: EntityId, broker: Long) {
    honor.recordAgainst(world, HonorAct.COMPLETE_COMMISSION, master, broker)
  }

  fun helpedTownsperson(world: World, master: EntityId, at: Vec3L) {
    honor.record(world, HonorAct.HELP_NPC, master, at)
  }

  companion object {
    /** master.md, Honorable Citizen: +20% honor penalty for robbers per level. */
    const val HONORABLE_CITIZEN_PER_LEVEL = 0.2

    /** What the methods touch, for a system that calls them from inside the tick. */
    val WRITES: ComponentClassSet = HonorService.WRITES + setOf(StatusEffects::class, IsStatusValueDirty::class)
  }
}
