package net.bestia.zone.item.container

import net.bestia.zone.item.persistence.ItemContainer

/** A row that holds an item container: a master, a player bestia or a chest. */
interface ContainerOwner {

  val container: ItemContainer
}
