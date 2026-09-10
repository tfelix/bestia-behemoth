package net.bestia.zone.ai.core.planner

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * That a belief an action concluded lasts as long as the belief is meant to.
 *
 * An effect has no call site to pass a retention at - [EffectWriteBack] writes every key the same way - so
 * before the key carried its own, everything an action concluded expired after the blackboard's default ten
 * minutes. That is invisible for a creature whose whole plan takes seconds and fatal for anything on the
 * scale of a day, where "I have already done this" has to outlive the doing of it.
 */
class EffectRetentionTest {

  private val permanent = StateKey<Int>("permanentBelief", retain = Blackboard.PERMANENT)
  private val fleeting = StateKey<Int>("fleetingBelief")
  private val observation = StateKey<Int>("anObservation", observed = true)

  @Test
  fun `a belief whose key says permanent outlives the sweep`() {
    val memory = Blackboard()

    apply(memory, permanent, 42)
    memory.tick(LONGER_THAN_DEFAULT)

    assertEquals(42, memory.get(permanent))
  }

  @Test
  fun `a belief that did not ask to be kept still expires`() {
    val memory = Blackboard()

    apply(memory, fleeting, 42)
    memory.tick(LONGER_THAN_DEFAULT)

    assertNull(memory.get(fleeting), "the default retention should still apply to a key that wants it")
  }

  @Test
  fun `an observation is still refused however long it wants to live`() {
    val memory = Blackboard()

    apply(memory, observation, 42)

    assertNull(memory.get(observation), "only perception may write an observed key")
  }

  @Test
  fun `retention is metadata, not identity`() {
    assertEquals(
      StateKey<Int>("same", retain = Blackboard.PERMANENT),
      StateKey<Int>("same"),
      "two keys with one name must address one slot however long each wants its value kept"
    )
    assertNotEquals(StateKey<Int>("one"), StateKey<Int>("other"))
  }

  /** What `AiActSystem` does when a plan step reports success. */
  private fun apply(memory: Blackboard, key: StateKey<Int>, value: Int) {
    EffectWriteBack.apply(
      before = WorldState.EMPTY,
      after = WorldState.EMPTY.with(key, value),
      individual = memory
    )
  }

  private companion object {
    /** Past `Blackboard.DEFAULT_RETAIN_TIME_SECONDS`, so anything on the default clock is swept. */
    const val LONGER_THAN_DEFAULT = 601f
  }
}
