package net.bestia.zoneserver.status

import mu.KotlinLogging
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.findOneOrThrow
import net.bestia.model.item.ItemRepository
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ItemComponent
import net.bestia.zoneserver.entity.component.OriginalStatusComponent
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class ItemOriginalStatusComponentFactory(
    private val itemDao: ItemRepository
) : OriginalStatusComponentFactory {

  override fun canBuildStatusFor(entity: Entity): Boolean {
    return entity.hasComponent(ItemComponent::class.java)
  }

  override fun buildComponent(entity: Entity): OriginalStatusComponent {
    val itemComp = entity.getComponent(ItemComponent::class.java)
    val item = itemDao.findOneOrThrow(itemComp.itemId)
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

    return OriginalStatusComponent(
        entityId = entity.id,
        element = element,
        statusValues = statusValues
    )
  }
}