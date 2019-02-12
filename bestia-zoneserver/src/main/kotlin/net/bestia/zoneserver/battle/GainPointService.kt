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

  fun neededGainPoints(currentEffortValue: Int): Int {
    return Math.floor((currentEffortValue + 1) * 0.25).toInt() * 2
  }

  fun updateEffortValues(entity: Entity, newEffortValues: BaseValues): Pair<GainPointComponent, StatusComponent> {
    val statusComponent = entity.getComponent(StatusComponent::class.java)
    val gainComponent = entity.getComponent(GainPointComponent::class.java)
    val effortValues = statusComponent.effortValues

    var currentGain = gainComponent.gainPoints
    val delta = newEffortValues - effortValues

    for (i in 1..delta.dexterity) {
      val neededGain = neededGainPoints(effortValues.dexterity)
      if(currentGain < neededGain) {
        break
      }

      currentGain -=
    }
  }
}