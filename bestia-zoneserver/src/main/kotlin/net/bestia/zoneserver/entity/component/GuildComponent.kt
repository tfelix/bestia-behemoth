package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Holds the data to correlate an entity to a guild.
 *
 * @author Thomas Felix
 */
data class GuildComponent(
    override val entityId: Long,

    @JsonProperty("gid")
    val guildId: Long,

    @JsonProperty("gn")
    val guildName: String,

    @JsonProperty("e")
    val emblem: String? = null,

    @JsonProperty("rn")
    val rankName: String
) : Component