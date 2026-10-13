package net.bestia.zone.battle.status

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes equal [CombatValues] one instance, so the mobs of a species share theirs. For unowned entities only: an
 * owner's values change as it plays and would just grow the pool.
 *
 * Strong, and capped at [MAX_SHARED] (past it a value is handed back unpooled): the set is a species at its level plus
 * the few buffs a mob can be under, and there is no Guava here for a weak interner.
 */
@Component
class CombatValuesPool {

  private val shared = ConcurrentHashMap<CombatValues, CombatValues>()

  val size: Int
    get() {
      return shared.size
    }

  fun share(values: CombatValues): CombatValues {
    shared[values]?.let { return it }

    // Approximate under a race, which only means a few entries past the cap.
    if (shared.size >= MAX_SHARED) {
      return values
    }

    return shared.putIfAbsent(values, values) ?: values
  }

  companion object {
    const val MAX_SHARED = 10_000
  }
}
