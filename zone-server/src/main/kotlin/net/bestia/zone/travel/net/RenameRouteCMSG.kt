package net.bestia.zone.travel.net

import net.bestia.zone.message.CMSG

data class RenameRouteCMSG(override val playerId: Long, val routeId: Long, val name: String) : CMSG
