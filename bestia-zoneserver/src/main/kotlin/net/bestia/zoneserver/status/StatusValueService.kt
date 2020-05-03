package net.bestia.zoneserver.status

import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

@Service
class StatusValueService(
    private val originalStatusComponentFactories: List<StatusComponentFactory>,
    private val defenseService: DefenseServices
) {

  fun buildStatusComponent(entity: Entity): StatusComponent {
    val statusValues = buildBaseStatusValues()
    val defense = defenseService.getDefense()

    // FIXME The whole status calculation
    val factory = originalStatusComponentFactories.find { it.canBuildStatusFor(entity) }
        ?: throw IllegalArgumentException("No factory found responsible for $entity")

    val level = entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1

    val statusBasedValues = BasicStatusBasedValues(
        statusValues = statusValues,
        level = level
    )

    return StatusComponent(
        entityId = entity.id,
        defense = defense,
        statusValues = statusValues,
        statusBasedValues = statusBasedValues,
        element = getCurrentElement()
    )
  }

  private fun getCurrentElement(): Element {
    return Element.NORMAL
  }

  private fun buildBaseStatusValues(): StatusValues {
    return BasicStatusValues()
  }
}