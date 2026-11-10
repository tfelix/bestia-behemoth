package net.bestia.zone.item.container

import net.bestia.zone.util.PlayerBestiaId

/** One of a master's own containers that goods can be handed between. */
sealed interface ItemHolder {

  data object Master : ItemHolder

  data class Bestia(val playerBestiaId: PlayerBestiaId) : ItemHolder

  /** A chest the master built, named by its structure row. */
  data class Chest(val structureId: Long) : ItemHolder
}
