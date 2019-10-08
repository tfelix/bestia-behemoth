package net.bestia.zoneserver.status

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.OriginalStatusComponent
import org.springframework.stereotype.Component

@Component
class GeneralOriginalStatusComponentFactory(
    private val originalStatusComponentFactories: List<OriginalStatusComponentFactory>
) {

  fun buildComponent(entity: Entity): OriginalStatusComponent {
    val factory = originalStatusComponentFactories.find { it.canBuildStatusFor(entity) }
        ?: throw IllegalArgumentException("No factory found responsible for $entity")

    return factory.buildComponent(entity)
  }
}