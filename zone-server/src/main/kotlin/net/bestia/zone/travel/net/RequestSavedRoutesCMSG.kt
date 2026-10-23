package net.bestia.zone.travel.net

import net.bestia.zone.message.CMSG

data class RequestSavedRoutesCMSG(override val playerId: Long) : CMSG
