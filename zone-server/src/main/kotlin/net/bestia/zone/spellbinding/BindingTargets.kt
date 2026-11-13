package net.bestia.zone.spellbinding

import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.bestia.WildBestia
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.BoundSpellCaster
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.PlayerOwnership
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/** Who passes a binding's [BindingFilter]: who may set it off, and who it may be cast at. */
class BindingTargets(private val world: World, private val owner: SpellBindingOwner) {

  /** The binder's party, read off any member's master in the world; only the binder while none is online. */
  private val allies: Set<AccountId> by lazy {
    val party = HashSet<AccountId>()
    world.query(PartyMembership::class).each { _ ->
      val members = get<PartyMembership>().members
      if (members.any { it.masterId == owner.masterId }) members.mapTo(party) { it.accountId }
    }

    party + owner.accountId
  }

  fun admits(filter: BindingFilter, candidate: EntityId): Boolean {
    if (!isCreature(candidate)) {
      return false
    }
    val candidateOwner = PlayerOwnership.ownerAccountOf(world, candidate)

    return when (filter) {
      BindingFilter.FOES -> candidateOwner != owner.accountId &&
          DamageGate.verdict(world, null, candidate, owner.accountId) == DamageGate.Verdict.ADMITTED

      BindingFilter.ALLIES -> candidateOwner != null && candidateOwner in allies
      BindingFilter.ANYONE_BUT_BINDER -> candidateOwner != owner.accountId
      BindingFilter.WILD_ONLY -> WildBestia.isWild(world, candidate)
    }
  }

  /** Neither the binder nor the binder's party, nor what any of them own. */
  fun isStranger(candidate: EntityId): Boolean {
    val candidateOwner = PlayerOwnership.ownerAccountOf(world, candidate)

    return candidateOwner == null || candidateOwner !in allies
  }

  private fun isCreature(id: EntityId): Boolean {
    return world.isAlive(id) && world.has(id, Health::class) && world.has(id, Position::class) &&
        !world.has(id, Dead::class) && !world.has(id, BoundSpellCaster::class)
  }

  companion object {
    val READS: ComponentClassSet = setOf(Health::class, Position::class, Dead::class, PartyMembership::class) +
        PlayerOwnership.READS + DamageGate.READS + WildBestia.READS
  }
}
