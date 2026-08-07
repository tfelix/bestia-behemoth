package net.bestia.zone.ai.core.state

/**
 * A typed handle into a [WorldState] / [Blackboard].
 *
 * The type parameter [T] is *phantom* (it is not stored) but it lets the rest
 * of the system stay type-safe: `WorldState.get(hunger)` returns an `Int?` and
 * `WorldState.get(position)` returns a `Vec3L?` without any casting at the
 * call site. This is what lets the same store hold both simple numerics
 * (Int 0..100) and complex objects (positions, item/location collections).
 *
 * Equality/hash are by [name] only, so two `StateKey<Int>("hunger")` created in
 * different places refer to the same slot. Neither [scope] nor [observed] participates in
 * that equality — both are metadata [net.bestia.zone.ai.core.planner.EffectWriteBack]
 * reads when deciding what to do with a write, not part of the key's identity.
 */
class StateKey<T>(
  val name: String,
  /** How far a write to this key propagates — see [MemoryScope]. */
  val scope: MemoryScope = MemoryScope.INDIVIDUAL,
  /**
   * True for a key that describes the world as *observed*: a position, a health value, whether a
   * hostile is visible. Perception is the only thing allowed to write it, and
   * [net.bestia.zone.ai.core.planner.EffectWriteBack] therefore refuses to persist it.
   *
   * Note this restricts *write-back*, not planning. The A* search must still be free to imagine an
   * observation changing — `walkTo(spot)` is only a useful action because the planner can simulate
   * standing on the spot afterwards — otherwise no plan involving movement could ever be found. What
   * would be wrong is carrying that hypothesis back into live memory once the action ran, because then
   * an agent that merely *decided* to walk somewhere would believe it had arrived. That was the
   * concrete defect in the old plan executor: it wrote every touched key back, so simulating a walk
   * teleported the agent's belief about its own position.
   *
   * Leave it false for a *belief* — remembered foraging spots, learned attack effectiveness, a drive
   * like hunger — which an action legitimately updates once its behaviour has actually succeeded.
   */
  val observed: Boolean = false,
) {
  override fun equals(other: Any?): Boolean = this === other || (other is StateKey<*> && name == other.name)
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = name
}
