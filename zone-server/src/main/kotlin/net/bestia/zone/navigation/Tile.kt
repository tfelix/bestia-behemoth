package net.bestia.zone.navigation

data class Tile(
  val height: Int,
  val canWalkLeft: Boolean,
  val canWalkRight: Boolean,
  val canWalkUp: Boolean,
  val canWalkDown: Boolean
)