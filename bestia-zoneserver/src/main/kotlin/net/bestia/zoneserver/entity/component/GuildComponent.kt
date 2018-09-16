package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Holds the data to correlate an entity to a guild.
 *
 * @author Thomas Felix
 */
data class GuildComponent(
        override val id: Long,
        override val entityId: Long,

        @JsonProperty("gid")
        var guildId: Int,

        @JsonProperty("gn")
        var guildName: String,

        @JsonProperty("e")
        var emblem: String?,

        @JsonProperty("rn")
        var rankName: String
) : Component