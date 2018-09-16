package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.zoneserver.entity.component.receiver.InSighReceiver
import net.bestia.zoneserver.entity.component.receiver.OwnerReceiver
import net.bestia.zoneserver.entity.component.transformer.LevelOnlyTransformer
import net.bestia.zoneserver.bestia.LevelService

/**
 * Level components allow entities to receive exp and level up.
 *
 * @author Thomas Felix
 */
@ClientSync([
  ClientDirective(receiver = InSighReceiver::class, transform = LevelOnlyTransformer::class),
  ClientDirective(receiver = OwnerReceiver::class)
])
class LevelComponent(id: Long) : Component(id, 0) {
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

  override fun toString(): String {
    return "LevelComponent[id: $id, level: $level, exp: $exp]"
  }
}
