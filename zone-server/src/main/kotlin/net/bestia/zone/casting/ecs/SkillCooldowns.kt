package net.bestia.zone.casting.ecs

import net.bestia.zone.ecs.core.Component

/**
 * When each of an entity's skills may be used again, as epoch millis. Not persisted: a cooldown is
 * seconds long, so losing it on a logout costs nothing.
 */
class SkillCooldowns(
  private val readyAtMillis: MutableMap<Long, Long> = mutableMapOf()
) : Component {

  fun isReady(skillId: Long, nowMillis: Long): Boolean {
    return nowMillis >= (readyAtMillis[skillId] ?: 0L)
  }

  fun start(skillId: Long, readyAtMillis: Long) {
    this.readyAtMillis[skillId] = readyAtMillis
  }
}
