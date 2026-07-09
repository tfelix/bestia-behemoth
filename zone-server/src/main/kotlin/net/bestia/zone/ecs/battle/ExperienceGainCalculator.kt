package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * Splits a mob's given exp across its attackers proportionally to the damage they
 * dealt, applying a bonus for each additional distinct player that participated in
 * the kill.
 */
@Component
class ExperienceGainCalculator(
  private val bestiaRepository: BestiaRepository
) {

  fun calculate(
    bestiaId: Long,
    damagePercentages: Map<EntityId, Float>,
    attackingPlayerCount: Int,
  ): Map<EntityId, Int> {
    val killedBestia = bestiaRepository.findByIdOrNull(bestiaId)

    if (killedBestia == null) {
      LOG.warn { "Bestia $bestiaId not found, can not determine earned EXP" }
      return emptyMap()
    }

    val givenExp = killedBestia.experienceReward
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
