package net.bestia.zone.guild

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.spawn.ecs.KillExpLevy
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A guild's tax on its members' kill EXP: the rank's percentage of every share, rounded down, goes to the guild.
 *
 * On the tick: it only reads the member's component and drops the tax into the [GuildExpLedger].
 */
@Component
class GuildExpLevy(
  private val ledger: GuildExpLedger,
) : KillExpLevy {

  override val reads: ComponentClassSet = setOf(GuildMembership::class, Master::class)

  override fun levy(world: World, masterEntityId: EntityId, amount: Int): Int {
    val membership = world.get(masterEntityId, GuildMembership::class) ?: return amount
    val masterId = world.get(masterEntityId, Master::class)?.masterId ?: return amount

    val tax = amount * membership.taxPercent / 100
    if (tax <= 0) {
      return amount
    }

    ledger.add(membership.guildId, masterId, tax.toLong())

    return amount - tax
  }
}
