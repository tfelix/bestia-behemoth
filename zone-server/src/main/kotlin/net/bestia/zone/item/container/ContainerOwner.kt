package net.bestia.zone.item.container

import net.bestia.zone.item.persistence.ItemContainer

/** A row that holds an item container: a master, a player bestia, a chest or a guild's storage. */
interface ContainerOwner {

  val container: ItemContainer
}
