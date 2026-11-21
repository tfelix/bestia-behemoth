package net.bestia.zone.item.container

import net.bestia.zone.util.PlayerBestiaId

/** A container that goods can be handed between: one of a master's own, or its guild's storage. */
sealed interface ItemHolder {

  data object Master : ItemHolder

  data class Bestia(val playerBestiaId: PlayerBestiaId) : ItemHolder

  /** A chest the master built, named by its structure row. */
  data class Chest(val structureId: Long) : ItemHolder

  /** Whoever reaches in was checked against the guild's ranks on the tick. */
  data class GuildStorage(val guildId: Long) : ItemHolder
}
