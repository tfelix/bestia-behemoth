package net.bestia.zone.item.container

import net.bestia.zone.item.persistence.ItemContainer

/** A row that holds an item container: a master or a player bestia. */
interface ContainerOwner {

  val container: ItemContainer
}
