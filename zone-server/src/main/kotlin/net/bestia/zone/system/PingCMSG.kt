package net.bestia.zone.system

import net.bestia.zone.message.CMSG

data class PingCMSG(override val playerId: Long) : CMSG

