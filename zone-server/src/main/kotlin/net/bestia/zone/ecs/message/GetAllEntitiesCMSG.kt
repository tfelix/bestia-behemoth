package net.bestia.zone.ecs.message

import net.bestia.zone.message.CMSG


data class GetAllEntitiesCMSG(override val playerId: Long) : CMSG