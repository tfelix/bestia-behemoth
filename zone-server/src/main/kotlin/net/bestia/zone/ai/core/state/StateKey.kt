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
 * different places refer to the same slot. None of [scope], [observed] or [retain] participates in
 * that equality — they are metadata [net.bestia.zone.ai.core.planner.EffectWriteBack]
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
  /**
   * How long a write to this key survives, in seconds.
   *
   * It belongs to the key because a fact's lifetime is a property of the fact, and only the key is in
   * scope everywhere it gets written. A belief written by an action's effect has no call site to pass a
   * retention at — [net.bestia.zone.ai.core.planner.EffectWriteBack] writes every key the same way —
   * so before this, anything an action concluded quietly expired ten minutes later whatever it meant.
   *
   * [Blackboard.PERMANENT] for a fact that ends when something ends it rather than when a timer does:
   * a drive, a latch a perception clears, a thing learned. The default suits a fact that is refreshed
   * often enough that its own staleness is the point.
   */
  val retain: Float = Blackboard.DEFAULT_RETAIN_TIME_SECONDS,
) {
  override fun equals(other: Any?): Boolean = this === other || (other is StateKey<*> && name == other.name)
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = name
}
