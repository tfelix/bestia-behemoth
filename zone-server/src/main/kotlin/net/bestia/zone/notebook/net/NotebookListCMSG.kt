package net.bestia.zone.notebook.net

import net.bestia.zone.message.CMSG

data class NotebookListCMSG(override val playerId: Long, val collection: String) : CMSG
