package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.BaseValues
import kotlin.math.floor

private val LOG = KotlinLogging.logger { }

class GainPointService {

  fun getAvailableGainPoints(playerBestiaId: Long): Int {
    return 0
  }

  fun addGainPoints(playerBestiaId: Long, newLevel: Int): Int {
    val earnedGainPoints = 4 + newLevel / 10

    val totalGainPoints = getAvailableGainPoints(playerBestiaId) + earnedGainPoints

    LOG.debug { "Player Bestia $playerBestiaId has $totalGainPoints" }
    // FIXME Save this new value in the DB

    return totalGainPoints
  }

  private fun neededGainPointsForUpgrade(currentEffortValue: Int): Int {
    return floor((currentEffortValue + 1) * 0.25).toInt() * 2
  }

  fun updateEffortValues(
      playerBestiaId: Long,
      desiredEffortValues: BaseValues,
      currentEffortValue: BaseValues
  ): BaseValues {
    var currentGain = getAvailableGainPoints(playerBestiaId)
    val delta = desiredEffortValues - currentEffortValue
    var updatedEffortValues = currentEffortValue

    for (i in 1..delta.hp) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.hp)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.hp + 1)
    }

    for (i in 1..delta.mana) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.mana)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.mana + 1)
    }

    for (i in 1..delta.stamina) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.stamina)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.stamina + 1)
    }

    for (i in 1..delta.agility) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.agility)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.agility + 1)
    }

    for (i in 1..delta.dexterity) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.dexterity)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.dexterity + 1)
    }

    for (i in 1..delta.willpower) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.willpower)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.willpower + 1)
    }

    for (i in 1..delta.intelligence) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.intelligence)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.intelligence + 1)
    }

    for (i in 1..delta.vitality) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.vitality)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.vitality + 1)
    }

    for (i in 1..delta.strength) {
      val neededGain = neededGainPointsForUpgrade(currentEffortValue.strength)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      updatedEffortValues = updatedEffortValues.copy(hp = updatedEffortValues.strength + 1)
    }

    return updatedEffortValues
  }
}