package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Holds the data to correlate an entity to a guild.
 *
 * @author Thomas Felix
 */
data class GuildComponent(
    override val entityId: Long,

    val guildId: Long,
    val guildName: String,
    val rankName: String
) : Component