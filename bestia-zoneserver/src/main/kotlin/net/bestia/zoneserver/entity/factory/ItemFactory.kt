package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.findOne
import net.bestia.model.geometry.Vec3
import net.bestia.model.item.ItemRepository
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.*
import net.bestia.zoneserver.status.GeneralOriginalStatusComponentFactory
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
@Component
class ItemFactory(
    private val itemDao: ItemRepository,
    private val idGenerator: IdGenerator,
    private val originalStatusComponentFactory: GeneralOriginalStatusComponentFactory
) {

  fun build(itemDbName: String, position: Vec3, amount: Int = 1): Entity {
    val item = itemDao.findItemByName(itemDbName)
        ?: itemDao.findOne(itemDbName.toLongOrNull() ?: 0)
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
        mesh = item.mesh
    )
    val tagComp = TagComponent(
        entityId = entityId,
        tags = setOf(TagComponent.ITEM)
    )
    val levelComp = LevelComponent(
        entityId = entityId,
        level = item.level
    )
    val itemComp = ItemComponent(
        entityId = entityId,
        itemId = item.id,
        itemDbName = item.databaseName
    )

    entity.addAllComponents(listOf(
        posComp,
        visualComp,
        tagComp,
        levelComp,
        itemComp
    ))

    val statusComp = originalStatusComponentFactory.buildComponent(entity)
    entity.addComponent(statusComp)

    return entity
  }
}
