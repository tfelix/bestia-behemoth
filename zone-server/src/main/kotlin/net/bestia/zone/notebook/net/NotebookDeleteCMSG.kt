package net.bestia.zone.notebook.net

import net.bestia.zone.message.CMSG

data class NotebookDeleteCMSG(
  override val playerId: Long,
  val collection: String,
  val key: String,
  val baseRevision: Long?,
) : CMSG
