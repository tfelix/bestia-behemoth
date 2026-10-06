package net.bestia.zone.socket.net

import net.bestia.zone.message.CMSG

data class PingCMSG(override val playerId: Long) : CMSG

