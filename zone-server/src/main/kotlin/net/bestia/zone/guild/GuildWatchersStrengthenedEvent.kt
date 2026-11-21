package net.bestia.zone.guild

import net.bestia.zone.BestiaEvent

/** A guild raised Strengthen Watchers to [level]. Published after the commit. */
class GuildWatchersStrengthenedEvent(source: Any, val guildId: Long, val level: Int) : BestiaEvent(source)
