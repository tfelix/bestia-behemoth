package net.bestia.zone.entity

import net.bestia.zone.message.CMSG

data class GetAllEntitiesCMSG(override val playerId: Long) : CMSG