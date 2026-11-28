package net.bestia.zone.honor

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Whether two masters are sworn to rival Orders, which changes what a kill between them costs.
 *
 * Guild mates are never rivals. The Circle opposes nobody until the weekly Reading names whom it opposes.
 */
@Component
class FactionRivalry {

  fun areOpposing(world: World, master: EntityId, other: EntityId): Boolean {
    val own = world.get(master, FactionMembership::class)?.faction ?: return false
    val theirs = world.get(other, FactionMembership::class)?.faction ?: return false
    if (areGuildMates(world, master, other)) {
      return false
    }

    return own.opposite == theirs
  }

  private fun areGuildMates(world: World, master: EntityId, other: EntityId): Boolean {
    val guild = world.get(master, GuildMembership::class)?.guildId ?: return false

    return guild == world.get(other, GuildMembership::class)?.guildId
  }

  companion object {
    val READS: ComponentClassSet = setOf(FactionMembership::class, GuildMembership::class)
  }
}
