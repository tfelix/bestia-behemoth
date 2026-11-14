package net.bestia.zone.spawn.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.spawn.MobLevelScaling
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * Splits a mob's given exp across its attackers proportionally to the damage they
 * dealt, applying a bonus for each additional distinct player that participated in
 * the kill.
 */
@Component
class ExperienceGainCalculator(
  private val bestiaCatalogue: BestiaCatalogue,
  private val levelScaling: MobLevelScaling = MobLevelScaling()
) {

  /**
   * @param killedLevel the level the dead creature was rolled at, which its species' reward is scaled to. Null for
   *   one with no level at all, which is paid the species' authored reward as is.
   */
  fun calculate(
    killedBestiaId: Long,
    killedLevel: Int?,
    damagePercentages: Map<EntityId, Float>,
    attackingPlayerCount: Int,
  ): Map<EntityId, Int> {
    val killedBestia = bestiaCatalogue.find(killedBestiaId)

    if (killedBestia == null) {
      LOG.warn { "Bestia $killedBestiaId not found, can not determine earned EXP" }
      return emptyMap()
    }

    val givenExp = if (killedLevel == null) killedBestia.experienceReward else levelScaling.experience(killedLevel, killedBestia)
    val extraPlayers = (attackingPlayerCount - 1).coerceAtLeast(0)
    val bonus = (BONUS_PER_EXTRA_PLAYER * extraPlayers).coerceAtMost(MAX_BONUS)
    val totalExp = floor(givenExp * (1f + bonus)).toInt()

    return damagePercentages.mapValues { (_, percent) -> floor(totalExp * percent).toInt() }
  }

  companion object {
    private const val BONUS_PER_EXTRA_PLAYER = 0.15f
    private const val MAX_BONUS = 1.5f
    private val LOG = KotlinLogging.logger { }
  }
}