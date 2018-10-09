package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.dao.ItemDAO
import net.bestia.model.domain.SpriteInfo
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.zoneserver.entity.component.TagComponent
import net.bestia.zoneserver.entity.component.VisualComponent
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
@Component
class ItemEntityFactory(
    private val itemDao: ItemDAO
) : AbstractFactory<ItemBlueprint>(ItemBlueprint::class.java) {

  override fun performBuild(entity: Entity, blueprint: ItemBlueprint) {
    if (blueprint.amount <= 0) {
      throw IllegalArgumentException("Amount can not be 0 or negative.")
    }

    val item = itemDao.findItemByName(blueprint.itemDbName)
        ?: throw IllegalArgumentException("Item in blueprint $blueprint was not found in database.")

    LOG.info { "Create Entity(Item): $item, amount: ${blueprint.amount} at ${blueprint.position}." }

    entity.addComponent(PositionComponent(
        entityId = entity.id,
        shape = blueprint.position
    ))

    entity.addComponent(VisualComponent(
        entityId = entity.id,
        visual = SpriteInfo.item(item.image)
    ))

    entity.addComponent(TagComponent(
        entityId = entity.id
    ).apply { this.add(TagComponent.ITEM, TagComponent.PERSIST) })

    entity.addComponent(StatusComponent(entity.id))
  }
}
