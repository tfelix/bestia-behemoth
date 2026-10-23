package net.bestia.zone.travel.net

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

data class SaveRouteCMSG(override val playerId: Long, val waypoints: List<Vec3L>, val name: String) : CMSG
