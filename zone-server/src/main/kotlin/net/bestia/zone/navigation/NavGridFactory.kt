package net.bestia.zone.navigation

import org.springframework.stereotype.Component

/**
 * This class takes a map and will convert it into a 2D projection which just indicates if a tile
 * is walkable or not. This is important for the short distance path finder.
 */
@Component
class NavGridFactory {
  fun buildNavGrid(): NavGrid {
    // Example: create a 5x5 grid with all tiles walkable and height 0
    val width = 100
    val height = 100
    val tiles = Array(height) { y ->
      Array(width) { x ->
        Tile(
          height = 0,
          canWalkLeft = x < width - 1,
          canWalkRight = x > 0,
          canWalkUp = y < height - 1,
          canWalkDown = y > 0
        )
      }
    }

    return NavGrid(width, height, tiles)
  }
}
