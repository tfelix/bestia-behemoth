package net.bestia.messages

import net.bestia.model.geometry.Point

data class MapMoveMessage(
    val accountId: Long,
    val coords: Point
)