package net.bestia.zone.faction

import net.bestia.worldgen.core.Faction
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** Swears masters to an Order and frees them again. Tick thread only, like every world write. */
@Service
class FactionService(
  private val config: FactionConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  enum class Refusal {
    ALREADY_SWORN,
    STILL_UNSWORN,
    LEVEL_TOO_LOW,
  }

  fun factionOf(world: World, master: EntityId): Faction? {
    return membershipOf(world, master).faction
  }

  fun refusalToSwear(world: World, master: EntityId): Refusal? {
    val membership = membershipOf(world, master)
    val level = world.get(master, Level::class)?.level ?: 1

    return when {
      membership.isSworn -> Refusal.ALREADY_SWORN
      !membership.mayPetition(clock.instant()) -> Refusal.STILL_UNSWORN
      level < config.minSwearLevel -> Refusal.LEVEL_TOO_LOW
      else -> null
    }
  }

  fun swear(world: World, master: EntityId, faction: Faction): Refusal? {
    refusalToSwear(world, master)?.let { return it }

    world.add(master, FactionMembership(faction = faction, swornAt = clock.instant()))
    return null
  }

  /** No Order takes the master back before [unswornFor] has passed. False if it was not sworn. */
  fun leave(world: World, master: EntityId, unswornFor: Duration): Boolean {
    if (!membershipOf(world, master).isSworn) {
      return false
    }

    world.add(master, FactionMembership(unswornUntil = clock.instant().plus(unswornFor)))
    return true
  }

  private fun membershipOf(world: World, master: EntityId): FactionMembership {
    return world.get(master, FactionMembership::class) ?: FactionMembership()
  }
}
