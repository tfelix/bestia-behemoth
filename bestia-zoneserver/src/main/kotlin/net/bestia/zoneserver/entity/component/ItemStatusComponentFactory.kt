package net.bestia.zoneserver.entity.component

import mu.KotlinLogging
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicDefense
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.findOneOrThrow
import net.bestia.model.item.ItemRepository
import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class ItemStatusComponentFactory(
    private val itemDao: ItemRepository
) : ComponentFactory<StatusComponent> {

  override fun buildComponent(entity: Entity): StatusComponent {
    val metaComp = entity.getComponent(MetadataComponent::class.java)
    val itemId = metaComp.tryGetAsLong(MetadataComponent.ITEM_ID)
        ?: throw IllegalArgumentException("No item id found in entity")
    val item = itemDao.findOneOrThrow(itemId)
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

    LOG.trace { "Build item '${item.databaseName}' status: $statusValues" }

    return StatusComponent(
        entityId = entity.id,
        element = element,
        statusValues = statusValues,
        defense = BasicDefense(0, 0),
        statusBasedValues = BasicStatusBasedValues(level = lv, statusValues = statusValues)
    )
  }
}