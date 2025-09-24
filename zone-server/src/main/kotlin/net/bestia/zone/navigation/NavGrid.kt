package net.bestia.zone.navigation

class NavGrid(
  val width: Int,
  val height: Int,
  val tiles: Array<Array<Tile>>
) {
  fun getTile(x: Int, y: Int): Tile? =
    if (x in 0 until width && y in 0 until height) tiles[y][x] else null
}