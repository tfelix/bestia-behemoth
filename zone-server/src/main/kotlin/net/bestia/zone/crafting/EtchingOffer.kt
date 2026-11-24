package net.bestia.zone.crafting

import net.bestia.zone.util.EntityId

/** Another master's Bestia, offered for etching at the Carving Table the Artificer works at. */
data class EtchingOffer(
  val bestiaId: EntityId,
  val speciesId: Long,
  val level: Int,
  val ownerName: String,
)
