package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.util.EntityId

/**
 * Whom a creature sees as an enemy, until real factions exist (the profile's `faction` is not read yet).
 *
 * Masters are everyone's enemy. What a player owns is the wild's enemy too, so a bestia out on its own can be
 * spotted and attacked; another player's bestia is not, or two parked bestias would fight on sight. A guard sees
 * more inside its area: see [isIntruder].
 */
object Hostility {

  val READS: ComponentClassSet =
    setOf(Master::class, OwnedBestia::class, StatusEffects::class, AiAgent::class, PartyMembership::class, GuildMembership::class)

  fun isHostile(world: World, self: EntityId, candidate: EntityId): Boolean {
    // A controlled creature has changed sides: it hunts the wild it came from and spares its controller's side.
    if (isControlled(world, self)) return isWild(world, candidate) && !isControlled(world, candidate)
    if (isControlled(world, candidate)) return isWild(world, self)

    if (world.has(candidate, Master::class)) return true

    return isWild(world, self) && world.has(candidate, OwnedBestia::class)
  }

  /**
   * What a guard drives out of its area: the wild that hunts on sight, and any other player's side but its own master's
   * party and guild. Whether a hit on them is legal at all is the damage gate's question, not this one.
   */
  fun isIntruder(world: World, guard: EntityId, candidate: EntityId): Boolean {
    if (isControlled(world, guard) || isControlled(world, candidate)) return isHostile(world, guard, candidate)
    if (isWild(world, candidate)) return huntsOnSight(world, candidate)

    return !isAlly(world, guard, candidate)
  }

  /** A wolf rather than a deer: a grazer that only fights back is no intruder until it attacks. */
  private fun huntsOnSight(world: World, id: EntityId): Boolean {
    return world.get(id, AiAgent::class)?.goals?.any { it.name == BestiaDomain.Goals.KILL_ENEMY.name } == true
  }

  private fun isAlly(world: World, guard: EntityId, candidate: EntityId): Boolean {
    val ownMaster = masterIdOf(world, guard) ?: return false
    val otherMaster = masterIdOf(world, candidate) ?: return false

    return ownMaster == otherMaster || mayShareGroup(world, ownMaster, otherMaster)
  }

  private fun masterIdOf(world: World, id: EntityId): Long? {
    return world.get(id, Master::class)?.masterId ?: world.get(id, OwnedBestia::class)?.masterId
  }

  /**
   * Whether the two masters share a party or guild. The rosters live on the masters, and either one answers, since a
   * roster lists everybody in it. With both masters away nobody can tell, and a guard spares rather than risks a friend.
   */
  private fun mayShareGroup(world: World, a: Long, b: Long): Boolean {
    var rosterSeen = false
    var shared = false
    world.query(Master::class).each { id ->
      val other = when (get<Master>().masterId) {
        a -> b
        b -> a
        else -> return@each
      }
      val partyMembers = world.get(id, PartyMembership::class)?.members.orEmpty().map { it.masterId }
      val guildMembers = world.get(id, GuildMembership::class)?.members.orEmpty().map { it.masterId }
      rosterSeen = true
      shared = shared || other in partyMembers || other in guildMembers
    }

    return shared || !rosterSeen
  }

  /** Wild and not under anybody's control. */
  fun isUntamed(world: World, id: EntityId): Boolean {
    return isWild(world, id) && !isControlled(world, id)
  }

  private fun isWild(world: World, id: EntityId): Boolean {
    return !world.has(id, OwnedBestia::class) && !world.has(id, Master::class)
  }

  private fun isControlled(world: World, id: EntityId): Boolean {
    return world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.CONTROLLED.id) ?: false
  }
}
