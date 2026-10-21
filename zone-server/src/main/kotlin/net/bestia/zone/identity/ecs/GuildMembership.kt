package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks a master entity as being in a guild, with the tax its rank pays, so the death system can levy kill EXP
 * with a plain component read.
 *
 * Kept up to date by `GuildService` once a change has committed - joining, leaving, a new rank, a rank's tax -
 * and added by `MasterEntitySpawner` when the master logs in.
 */
data class GuildMembership(
  val guildId: Long,
  val taxPercent: Int,
) : Component
