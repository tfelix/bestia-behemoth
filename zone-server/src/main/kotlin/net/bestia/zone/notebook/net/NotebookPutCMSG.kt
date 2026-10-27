package net.bestia.zone.notebook.net

import net.bestia.zone.message.CMSG

/** A null [key] lets the server pick one; a null [baseRevision] lets the last write win. */
data class NotebookPutCMSG(
  override val playerId: Long,
  val collection: String,
  val key: String?,
  val json: String,
  val baseRevision: Long?,
) : CMSG
