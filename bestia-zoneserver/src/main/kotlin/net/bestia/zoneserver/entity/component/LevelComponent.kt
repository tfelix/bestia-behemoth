package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.zoneserver.bestia.LevelService

/**
 * Level components allow entities to receive exp and level up.
 *
 * @author Thomas Felix
 */
data class LevelComponent(
    override val entityId: Long,

    @JsonProperty("lv")
    val level: Int = 1,

    @JsonProperty("e")
    val exp: Int = 0
) : Component
