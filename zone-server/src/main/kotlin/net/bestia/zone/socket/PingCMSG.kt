package net.bestia.zone.socket

import net.bestia.zone.message.CMSG

data class PingCMSG(override val playerId: Long) : CMSG

