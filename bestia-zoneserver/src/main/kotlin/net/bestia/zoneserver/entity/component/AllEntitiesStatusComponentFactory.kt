package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Component
import java.lang.IllegalStateException

@Component
class AllEntitiesStatusComponentFactory(
    private val itemStatusComponentFactory: ItemStatusComponentFactory,
    private val mobStatusComponentFactory: MobOriginalStatusComponentFactory
) : ComponentFactory<StatusComponent> {
  override fun buildComponent(entity: Entity): StatusComponent {
    return when {
      isEntityItem(entity) -> {
        itemStatusComponentFactory.buildComponent(entity)
      }
      isMobEntity(entity) -> {
        mobStatusComponentFactory.buildComponent(entity)
      }
      else -> {
        throw IllegalStateException("Could not found suitable StatusComponent factory for entity: $entity")
      }
    }
  }

  private fun isEntityItem(entity: Entity): Boolean {
    val metaComp = entity.tryGetComponent(MetadataComponent::class.java)
        ?: return false

    return metaComp.containsKey(MetadataComponent.ITEM_ID)
  }

  private fun isMobEntity(entity: Entity): Boolean {
    return entity.tryGetComponent(MetadataComponent::class.java)?.let {
      it.containsKey(MetadataComponent.MOB_BESTIA_ID) &&
          !it.containsKey(MetadataComponent.MOB_PLAYER_BESTIA_ID)
    } ?: false
  }
}