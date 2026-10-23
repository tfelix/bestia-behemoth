package net.bestia.zone.travel.net

import net.bestia.zone.message.CMSG

data class DeleteRouteCMSG(override val playerId: Long, val routeId: Long) : CMSG
