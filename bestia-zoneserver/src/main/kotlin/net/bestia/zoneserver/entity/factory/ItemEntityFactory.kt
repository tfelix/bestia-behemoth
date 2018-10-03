package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.dao.ItemDAO
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.model.domain.Item
import net.bestia.model.domain.SpriteInfo
import net.bestia.model.geometry.Point

import java.util.Objects

private val LOG = KotlinLogging.logger { }

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
class ItemEntityFactory(
    private val itemDao: ItemDAO
) {

  /**
   * Creates an dropped item entity on the given position with the name.
   *
   * @param itemDbName The item DB name of the item to spawn.
   * @param position   Location where to spawn the item entity.
   * @param amount     The amount of items to spawn at this location.
   * @return The created entity.
   */
  fun build(itemDbName: String, position: Point, amount: Int): EntityBlueprint {
    val item = itemDao.findItemByName(itemDbName)
    return build(item, position, amount)
  }

  fun build(item: Item, position: Point, amount: Int): EntityBlueprint {
    if (amount <= 0) {
      throw IllegalArgumentException("Amount can not be 0 or negative.")
    }

    LOG.info("Spawning item: {}, amount: {} on {}.", item, amount, position)

    val posComp = PositionComponent()
    val posSetter = PositionComponentSetter(position)
    val visSetter = VisibleComponentSetter(SpriteInfo.item(item.image))
    val tagSetter = TagComponentSetter(TagComponent.ITEM, TagComponent.PERSIST)
    val statusSetter = ItemStatusComponentSetter(item)

    val compSetter = EntityFactory.Companion.makeSet(
        posSetter,
        visSetter,
        tagSetter,
        statusSetter)

    return entityFactory.buildEntity(ITEM_BLUEPRINT, compSetter)
  }
}

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
class ItemEntityBuilder(
    private val itemDao: ItemDAO
) {

  /**
   * Creates an dropped item entity on the given position with the name.
   *
   * @param itemDbName The item DB name of the item to spawn.
   * @param position   Location where to spawn the item entity.
   * @param amount     The amount of items to spawn at this location.
   * @return The created entity.
   */
  fun build(itemDbName: String, position: Point, amount: Int): EntityBlueprint {
    val item = itemDao.findItemByName(itemDbName)
    return build(item, position, amount)
  }

  fun build(item: Item, position: Point, amount: Int): EntityBlueprint {
    if (amount <= 0) {
      throw IllegalArgumentException("Amount can not be 0 or negative.")
    }

    LOG.info("Spawning item: {}, amount: {} on {}.", item, amount, position)

    val posComp = PositionComponent()
    val posSetter = PositionComponentSetter(position)
    val visSetter = VisibleComponentSetter(SpriteInfo.item(item.image))
    val tagSetter = TagComponentSetter(TagComponent.ITEM, TagComponent.PERSIST)
    val statusSetter = ItemStatusComponentSetter(item)

    val compSetter = EntityFactory.Companion.makeSet(
        posSetter,
        visSetter,
        tagSetter,
        statusSetter)

    return entityFactory.buildEntity(ITEM_BLUEPRINT, compSetter)
  }
}
