package net.bestia.worldgen.place

import net.bestia.worldgen.vector.Vec2d

/** A group of place regions around a capital, or around a landmark where no town is near. */
data class Province(
  /** Dense index from zero. */
  val index: Int,

  /** The capital's name, or the name of the region the province grew from. */
  val name: String,

  val seat: Seat,

  /** The place regions it holds, ascending. Its seat region is one of them. */
  val regions: List<Int>,
) {

  /** What a province grew from. */
  data class Seat(
    val kind: SeatKind,
    val position: Vec2d,
    val region: Int,
    /** Index into the chronicle's settlements for a capital, -1 otherwise. */
    val settlementIndex: Int = -1,
  )

  enum class SeatKind {
    CITY,
    TOWN,
    /** A home settlement too small to be a town. */
    VILLAGE,
    /** A Wound or a lair, far from any town. */
    LANDMARK,
    /** Plain wilds, far from any town or landmark. */
    WILD,
  }
}
