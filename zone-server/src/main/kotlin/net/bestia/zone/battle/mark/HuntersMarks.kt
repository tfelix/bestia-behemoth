package net.bestia.zone.battle.mark

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.HuntersMarked
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/** What a Hunter's Mark on a prey gives the hunter who put it there. */
object HuntersMarks {

  val READS: ComponentClassSet = setOf(StatusEffects::class, Account::class, Master::class)

  /** The level of the mark [hunterId] put on [preyId]; 0 when it has none from them. */
  fun levelOn(world: World, hunterId: EntityId, preyId: EntityId): Int {
    val mark = world.get(preyId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.HUNTERS_MARK.id && it.sourceEntityId == hunterId }

    return mark?.level ?: 0
  }

  /** A hunter sees through its prey's Hide and Cloak. */
  fun seesThrough(world: World, hunterId: EntityId, preyId: EntityId): Boolean {
    return levelOn(world, hunterId, preyId) > 0
  }

  /** The players whose hunters marked [preyId]. */
  fun hunterAccountsOf(world: World, preyId: EntityId): Set<AccountId> {
    val marks = world.get(preyId, StatusEffects::class)?.activeEffects.orEmpty()
      .filter { it.definitionId == StatusEffectId.HUNTERS_MARK.id }

    return marks.mapNotNullTo(HashSet()) { mark -> mark.sourceEntityId?.let { world.get(it, Account::class)?.accountId } }
  }

  /** Whether a hunter played by the master [masterId] marked [preyId]. */
  fun isMarkedByMaster(world: World, preyId: EntityId, masterId: Long): Boolean {
    val marks = world.get(preyId, StatusEffects::class)?.activeEffects.orEmpty()
      .filter { it.definitionId == StatusEffectId.HUNTERS_MARK.id }

    return marks.any { mark -> mark.sourceEntityId?.let { world.get(it, Master::class)?.masterId } == masterId }
  }

  /** Percent more chance for [attackerId] to crit [targetId], when it marked it. */
  fun critPercentAgainst(world: World, attackerId: EntityId, targetId: EntityId): Int {
    val level = levelOn(world, attackerId, targetId)

    return if (level > 0) HuntersMarked.critPercent(level) else 0
  }
}
