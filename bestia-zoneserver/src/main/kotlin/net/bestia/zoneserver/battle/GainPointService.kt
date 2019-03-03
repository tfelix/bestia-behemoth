package net.bestia.zoneserver.battle

import net.bestia.model.bestia.BaseValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.GainPointComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent

class GainPointService {

  fun earnGainPoints(entity: Entity): GainPointComponent {
    val gainPointComponent = entity.getComponent(GainPointComponent::class.java)
    val newLevel = entity.tryGetComponent(LevelComponent::class.java)?.level
        ?: return gainPointComponent

    val earnedGainPoints = 4 + newLevel / 10

    return gainPointComponent.copy(gainPoints = gainPointComponent.gainPoints + earnedGainPoints)
  }

  fun neededGainPointsForUpgrade(currentEffortValue: Int): Int {
    return Math.floor((currentEffortValue + 1) * 0.25).toInt() * 2
  }

  fun updateEffortValues(entity: Entity, newEffortValues: BaseValues): Pair<GainPointComponent, StatusComponent> {
    val statusComponent = entity.getComponent(StatusComponent::class.java)
    val gainComponent = entity.getComponent(GainPointComponent::class.java)
    var effortValues = statusComponent.effortValues

    var currentGain = gainComponent.gainPoints
    val delta = newEffortValues - effortValues

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
        statusComponent.copy(effortValues = effortValues)
    )
  }
}