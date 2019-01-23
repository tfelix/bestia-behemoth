package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.geometry.Point
import net.bestia.model.item.ItemRepository
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGeneratorService
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
@Component
class ItemFactory(
    private val itemDao: ItemRepository,
    private val idGenerator: IdGeneratorService
) {

  fun build(itemDbName: String, position: Point, amount: Int = 1): Entity {
    val item = itemDao.findItemByName(itemDbName)
        ?: throw IllegalArgumentException("Item in blueprint $itemDbName was not found in database.")

    LOG.info { "Create Entity(Item): $item, amount: $amount at $position." }

    val entityId = idGenerator.newId()
    val entity = Entity(entityId)
    val posComp = PositionComponent(
        entityId = entityId,
        shape = position
    )
    val visualComp = VisualComponent(
        entityId = entityId,
        visual = SpriteInfo.item(item.image)
    )
    val tagComp = TagComponent(
        entityId = entityId,
        tags = setOf(TagComponent.ITEM)
    )
    val levelComp = LevelComponent(
        entityId = entityId,
        level = item.level
    )
    val statusComp = StatusComponent.forItem(entityId, item)

    entity.addAllComponents(listOf(
        posComp,
        visualComp,
        tagComp,
        levelComp,
        statusComp
    ))

    return entity
  }
}
