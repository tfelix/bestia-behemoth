package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.findOne
import net.bestia.model.geometry.Vec3
import net.bestia.model.item.Item
import net.bestia.model.item.ItemRepository
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
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
    private val idGenerator: IdGenerator
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

    entity.addAllComponents(listOf(
        posComp,
        visualComp,
        tagComp,
        levelComp
    ))

    val statusComp = makeStatusComponent(entity, item)
    entity.addComponent(statusComp)

    return entity
  }

  private fun makeStatusComponent(entity: Entity, item: Item): OriginalStatusComponent {
    val lv = item.level

    val vitality = 10
    val element = Element.NORMAL // TODO Improve Element detection

    val str = 1
    val vit = vitality * 2 * lv / 100 + 5
    val intel = 1
    val will = 1
    val agi = 1
    val dex = 1

    val statusValues = BasicStatusValues(
        str,
        vit,
        intel,
        will,
        agi,
        dex
    )

    return OriginalStatusComponent(
        entityId = entity.id,
        element = element,
        statusValues = statusValues
    )
  }
}
