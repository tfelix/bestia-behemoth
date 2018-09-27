package net.bestia.zoneserver.entity.factory

import net.bestia.zoneserver.entity.Entity
import net.bestia.entity.component.*
import net.bestia.zoneserver.entity.component.*
import net.bestia.zoneserver.entity.component.TagComponent.Tag
import net.bestia.model.dao.ItemDAO
import net.bestia.model.domain.Item
import net.bestia.model.domain.SpriteInfo
import net.bestia.model.geometry.Point
import org.springframework.beans.factory.annotation.Autowired

import java.util.Objects

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
@org.springframework.stereotype.Component
class ItemEntityBuilder(

) : EntityBuilder {

  private val entityFactory: EntityFactory
  private val itemDao: ItemDAO

  @Autowired
  constructor(entityFactory: EntityFactory, itemDao: ItemDAO) {

    this.entityFactory = Objects.requireNonNull(entityFactory)
    this.itemDao = Objects.requireNonNull(itemDao)
  }

  /**
   * Creates an dropped item entity on the given position with the name.
   *
   * @param itemDbName The item DB name of the item to spawn.
   * @param position   Location where to spawn the item entity.
   * @param amount     The amount of items to spawn at this location.
   * @return The created entity.
   */
  fun build(itemDbName: String, position: Point, amount: Int): Entity {
    val item = itemDao.findItemByName(itemDbName)
    return build(item, position, amount)
  }

  fun build(item: Item, position: Point, amount: Int): Entity {
    Objects.requireNonNull(item)
    Objects.requireNonNull(position)

    if (amount <= 0) {
      throw IllegalArgumentException("Amount can not be 0 or negative.")
    }

    LOG.info("Spawning item: {}, amount: {} on {}.", item, amount, position)

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

  companion object {

    private val ITEM_BLUEPRINT: Blueprint

    init {
      val builder = Blueprint.Builder()
      builder.addComponent(VisualComponent::class.java)
          .addComponent(PositionComponent::class.java)
          .addComponent(InventoryComponent::class.java)
          .addComponent(TagComponent::class.java)
          .addComponent(StatusComponent::class.java)

      ITEM_BLUEPRINT = builder.build()
    }
  }
}
