package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.zoneserver.bestia.LevelService

/**
 * Level components allow entities to receive exp and level up.
 *
 * @author Thomas Felix
 */
data class LevelComponent(
    override val id: Long,
    override val entityId: Long
) : Component {

  @JsonProperty("lv")
  var level: Int = 1
    set(value) {
      field = when {
        value < 0 -> 1
        value > LevelService.MAX_LEVEL -> LevelService.MAX_LEVEL
        else -> value
      }
    }

  @JsonProperty("e")
  var exp: Int = 0
    set(value) {
      field = when {
        value < 0 -> 0
        else -> value
      }
    }
}
