package net.bestia.zoneserver.environment

import net.bestia.model.bestia.TemperatureKind
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.TemperatureComponent
import org.springframework.stereotype.Service

/**
 * Calculates the ideal temperature zones for entities.
 */
@Service
class EntityTemperatureRangeService {

  private val temperatureTable = mapOf(
      TemperatureKind.LOW to Pair(-20, 15),
      TemperatureKind.MEDIUM to Pair(10, 30),
      TemperatureKind.HIGH to Pair(25, 45)
  )

  fun calculateTemperatureComponent(entity: Entity, temperatureKind: TemperatureKind): TemperatureComponent {
    val tempComp = entity.getComponent(TemperatureComponent::class.java)

    val minMax = temperatureTable[temperatureKind]
        ?: error("Temperature lookup missing for $temperatureKind")

    return tempComp.copy(
        // TODO later factor in equipments, status effects etc.
        minTolerableTemperature = minMax.first,
        maxTolerableTemperature = minMax.second,
        currentTemperature = getCurrentTemperatureForEntity(entity)
    )
  }

  /**
   * Check if equipment effects, status effects have influence on the current temperature.
   */
  private fun getCurrentTemperatureForEntity(entity: Entity): Float {
    // TODO tap into the weather system and request the current temperature on the current position in the world.
    val position = entity.tryGetComponent(PositionComponent::class.java)
        ?: error("Can not calculate temperature for entity '$entity' if no position component is present")

    println(position)

    return 25f
  }
}