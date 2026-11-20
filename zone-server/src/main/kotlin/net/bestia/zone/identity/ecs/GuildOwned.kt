package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Belongs to a guild as a whole, such as its war banner. Its members cannot harm it, and of everybody else only the
 * members of guilds with Official Guild Approval may.
 */
data class GuildOwned(
  val guildId: Long,
) : Component
