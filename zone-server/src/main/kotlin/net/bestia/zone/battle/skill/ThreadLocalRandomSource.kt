package net.bestia.zone.battle.skill

import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * A [Random] that draws from the *calling* thread's generator.
 *
 * `ThreadLocalRandom.current()` returns the generator belonging to the thread that calls it, and its contract
 * is that the result must not be shared. Held in a singleton field - which is what a Spring `@Component`
 * building its strategies once amounts to - it hands the tick thread the generator of whichever thread
 * happened to build the bean, which is exactly the sharing the class forbids.
 *
 * Every public method of [Random] is defined in terms of [next], so forwarding that one is enough.
 */
object ThreadLocalRandomSource : Random() {

  override fun next(bits: Int): Int = ThreadLocalRandom.current().nextInt() ushr (32 - bits)
}
