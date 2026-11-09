package net.bestia.zone.structure

import net.bestia.worldgen.derived.Edges

/** A border of a cell, by name, for `structure-pieces.yml`. */
enum class WallSide(val edge: Int) {
  X_PLUS(Edges.X_PLUS),
  Y_PLUS(Edges.Y_PLUS),
  X_MINUS(Edges.X_MINUS),
  Y_MINUS(Edges.Y_MINUS)
}
