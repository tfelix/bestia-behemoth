package net.bestia.zoneserver.battle

import net.bestia.model.bestia.BaseValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.EffortValueComponent
import net.bestia.zoneserver.entity.component.GainPointComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import kotlin.math.floor

// TODO Test it
class GainPointService {

  fun addGainPoints(entity: Entity): GainPointComponent {
    val gainPointComponent = entity.getComponent(GainPointComponent::class.java)
    val newLevel = entity.tryGetComponent(LevelComponent::class.java)?.level
        ?: return gainPointComponent

    val earnedGainPoints = 4 + newLevel / 10

    return gainPointComponent.copy(gainPoints = gainPointComponent.gainPoints + earnedGainPoints)
  }

  private fun neededGainPointsForUpgrade(currentEffortValue: Int): Int {
    return floor((currentEffortValue + 1) * 0.25).toInt() * 2
  }

  fun updateEffortValues(entity: Entity, desiredEffortValues: BaseValues): Pair<GainPointComponent, EffortValueComponent> {
    val effortComponent = entity.getComponent(EffortValueComponent::class.java)
    val gainComponent = entity.getComponent(GainPointComponent::class.java)
    var effortValues = effortComponent.effortValues

    var currentGain = gainComponent.gainPoints
    val delta = desiredEffortValues - effortValues

    for (i in 1..delta.hp) {
      val neededGain = neededGainPointsForUpgrade(effortValues.hp)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.hp + 1)
    }

    for (i in 1..delta.mana) {
      val neededGain = neededGainPointsForUpgrade(effortValues.mana)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.mana + 1)
    }

    for (i in 1..delta.stamina) {
      val neededGain = neededGainPointsForUpgrade(effortValues.stamina)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.stamina + 1)
    }

    for (i in 1..delta.agility) {
      val neededGain = neededGainPointsForUpgrade(effortValues.agility)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.agility + 1)
    }

    for (i in 1..delta.dexterity) {
      val neededGain = neededGainPointsForUpgrade(effortValues.dexterity)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.dexterity + 1)
    }

    for (i in 1..delta.willpower) {
      val neededGain = neededGainPointsForUpgrade(effortValues.willpower)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.willpower + 1)
    }

    for (i in 1..delta.intelligence) {
      val neededGain = neededGainPointsForUpgrade(effortValues.intelligence)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.intelligence + 1)
    }

    for (i in 1..delta.vitality) {
      val neededGain = neededGainPointsForUpgrade(effortValues.vitality)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.vitality + 1)
    }

    for (i in 1..delta.strength) {
      val neededGain = neededGainPointsForUpgrade(effortValues.strength)
      if (currentGain < neededGain) {
        break
      }
      currentGain -= neededGain
      effortValues = effortValues.copy(hp = effortValues.strength + 1)
    }

    return Pair(
        gainComponent.copy(gainPoints = currentGain),
        effortComponent.copy(effortValues = effortValues)
    )
  }
}