package net.bestia.zone.guild

import net.bestia.zone.BestiaEvent

/** A guild learned Official Guild Approval. Published after the commit. */
class GuildApprovedEvent(source: Any, val guildId: Long) : BestiaEvent(source)
