package net.bestia.zone.weather

/** The doomsday as the sky shows it at a place. A port, because the doomsday sits above the weather. */
fun interface DoomSkies {

  /** At the voxel column [x], [y]. Read on the tick, so it must not touch the database. */
  fun at(x: Long, y: Long): DoomSky
}

/** [kind] is the doomsday's kind, empty while none is felt; [level] runs from 0 (not felt) to [HEART]. */
data class DoomSky(val kind: String, val level: Int) {

  companion object {
    const val HEART = 4

    val CLEAR = DoomSky("", 0)
  }
}
