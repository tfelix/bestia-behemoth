package net.bestia.zoneserver.entity.component.transformer

import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.entity.component.transform.SyncTransformer

/**
 * Only transmits the condition value.
 */
class StatusOnlyConditionTransformer: SyncTransformer<StatusComponent> {
  override fun transform(component: StatusComponent): StatusComponent {
    return StatusComponent(component.id).also {
      it.conditionValues.set(component.conditionValues)
    }
  }
}