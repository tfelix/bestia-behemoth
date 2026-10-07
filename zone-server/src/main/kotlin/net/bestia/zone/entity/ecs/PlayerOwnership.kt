package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/** Whom an entity belongs to, which is what the damage gate weighs on both sides of a blow. */
object PlayerOwnership {

  val READS: ComponentClassSet = setOf(Account::class, PlayerStructureIdentity::class)

  /** Masters and their bestias carry the owner's [Account]; a station carries the row of whoever built it. */
  fun isPlayerOwned(world: World, id: EntityId): Boolean {
    return world.has(id, Account::class) || world.has(id, PlayerStructureIdentity::class)
  }

  fun ownerAccountOf(world: World, id: EntityId): AccountId? {
    return world.get(id, Account::class)?.accountId ?: world.get(id, PlayerStructureIdentity::class)?.ownerAccountId
  }
}
