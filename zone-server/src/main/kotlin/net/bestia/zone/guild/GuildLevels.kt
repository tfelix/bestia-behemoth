package net.bestia.zone.guild

import org.springframework.stereotype.Component

/**
 * A guild's level curve, seats and skill points.
 *
 * The curve is Ragnarok Online's guild table - 2M, 4M, 7M, 11M ... 56M at level 10, each step one million more
 * than the last - with its million replaced by [GuildConfig.expUnit]. RO's table sits beside a master curve that
 * needs ~100M per level near the top; Bestia's master needs a few hundred thousand, so the unit is scaled down
 * to keep Guild Extension 10 a goal for a busy guild of a few weeks rather than of years.
 */
@Component
class GuildLevels(
  private val config: GuildConfig,
) {

  data class Progress(val level: Int, val exp: Long, val levelsGained: Int)

  /** EXP to go from [level] to the next, or 0 at the highest level. */
  fun requiredExp(level: Int): Long {
    if (level >= config.maxLevel) {
      return 0
    }

    return config.expUnit * (level.toLong() * (level + 1) / 2 + 1)
  }

  /** Adds [gained] to a guild at [level] with [exp] towards the next, carrying over every level reached. */
  fun addExp(level: Int, exp: Long, gained: Long): Progress {
    var newLevel = level
    var newExp = exp + gained
    var levelsGained = 0

    while (newLevel < config.maxLevel) {
      val required = requiredExp(newLevel)
      if (newExp < required) {
        break
      }
      newExp -= required
      newLevel++
      levelsGained++
    }

    // Nothing to save up for at the top.
    if (newLevel >= config.maxLevel) {
      newExp = 0
    }

    return Progress(newLevel, newExp, levelsGained)
  }

  fun maxMembers(extensionLevel: Int): Int {
    return config.baseMaxMembers + extensionLevel * config.membersPerExtensionLevel
  }

  /** One point per level, the founding level included, less those already spent on guild skills. */
  fun skillPoints(level: Int, spentSkillPoints: Int): Int {
    return (level - spentSkillPoints).coerceAtLeast(0)
  }
}
