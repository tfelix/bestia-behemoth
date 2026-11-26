package net.bestia.zone.doomsday

import net.bestia.worldgen.vector.Vec2d
import kotlin.random.Random

/**
 * One kind of doomsday. The director asks it how the players stand against it and lets it act on the world; the
 * clock itself stays in [DoomClock].
 */
interface DoomThreat {

  val kind: DoomKind

  val shape: DoomShape

  /** The lesser threat this one also runs as, before onset, or null if it has none. */
  val foreshadow: ForeshadowKind?
    get() {
      return null
    }

  /** Whether this world has what the threat needs, such as a Wound or a sealed world boss. */
  fun fits(): Boolean

  /** Where the threat begins in this world, in metres. Only asked when it [fits]. */
  fun origin(random: Random): Vec2d

  /** How much the world feeds the threat right now, 0 to 1. Speeds the clock up. */
  fun pressure(): Double {
    return 0.0
  }

  /** How much of the threat the players hold right now, 0 to 1. Slows the clock down. */
  fun containment(): Double {
    return 0.0
  }

  fun onStage(from: DoomStage, to: DoomStage) {
  }

  /**
   * Lets the threat act for [realSeconds], and answers how far the players' deeds pushed the clock since the last
   * step: positive brings the end closer, negative holds it off.
   */
  fun step(fate: DoomFate, realSeconds: Double): Double {
    return 0.0
  }
}
