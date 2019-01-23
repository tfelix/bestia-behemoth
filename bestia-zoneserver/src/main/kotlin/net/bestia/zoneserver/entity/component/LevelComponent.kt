package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

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
    val exp: Long = 0
) : Component {
  init {
    if (level < 0 || level > MAX_LEVEL) {
      throw IllegalArgumentException("Level must be between 1 and $MAX_LEVEL")
    }
  }

  /**
   * Adds a amount of experience points to a entity with a level component. It
   * will check if a level up has occurred and recalculate status points if
   * neccesairy.
   *
   * @param entity
   * @param exp
   */
  fun addExp(exp: Long): LevelComponent {
    if (exp < 0) {
      throw IllegalArgumentException("EXP can not be negative")
    }

    LOG.trace { "addExp(): $entityId gains $exp exp" }

    return checkLevelup(copy(exp = this.exp + exp))
  }

  companion object {
    const val MAX_LEVEL = 10

    private fun checkLevelup(levelComponent: LevelComponent): LevelComponent {
      val neededExp = Math.round(Math.pow(levelComponent.level.toDouble(), 3.0) / 10 + 15.0 + levelComponent.level * 1.5).toInt()

      LOG.trace { "checkLevelup(): ${levelComponent.entityId} has ${levelComponent.exp}/$neededExp exp for levelup" }

      return if (levelComponent.exp > neededExp && levelComponent.level < MAX_LEVEL) {
        checkLevelup(levelComponent.copy(
            exp = levelComponent.exp - neededExp,
            level = levelComponent.level + 1
        ))
      } else {
        levelComponent
      }
    }
  }
}
