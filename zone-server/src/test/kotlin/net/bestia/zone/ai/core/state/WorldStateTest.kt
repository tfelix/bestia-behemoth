package net.bestia.zone.ai.core.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldStateTest {

  private val hunger = StateKey<Int>("worldStateTest.hunger")
  private val target = StateKey<String?>("worldStateTest.target")
  private val seen = StateKey<Boolean>("worldStateTest.seen", observed = true)

  @Test
  fun `a write returns a new state and leaves the old one alone`() {
    val before = WorldState.of(hunger to 10)

    val after = before.with(hunger, 20).with(seen, true)

    assertEquals(10, before.get(hunger))
    assertFalse(before.contains(seen))
    assertEquals(20, after.get(hunger))
    assertEquals(true, after.get(seen))
  }

  @Test
  fun `a key set to null is held, unlike a removed one`() {
    val state = WorldState.of(target to null)

    assertTrue(state.contains(target))
    assertNull(state.get(target))
    assertFalse(state.without(target).contains(target))
    assertNotEquals(WorldState.EMPTY, state)
  }

  @Test
  fun `states with the same entries are equal whatever order they were built in`() {
    val one = WorldState.EMPTY.with(hunger, 5).with(seen, false).with(target, "tree")
    val other = WorldState.from(mapOf(target to "tree", seen to false, hunger to 5))

    assertEquals(one, other)
    assertEquals(one.hashCode(), other.hashCode())
    assertNotEquals(one, other.with(hunger, 6))
  }

  @Test
  fun `a merge layers the other state on top`() {
    val world = WorldState.of(hunger to 1, target to "rock")
    val individual = WorldState.of(hunger to 9, seen to true)

    val merged = world.mergedWith(individual)

    assertEquals(WorldState.of(hunger to 9, target to "rock", seen to true), merged)
  }

  /** The write-back reads a key's metadata, so the keys handed out must be the declared ones. */
  @Test
  fun `the keys keep their metadata`() {
    val state = WorldState.of(seen to true, hunger to 3)

    assertTrue(state.keys().single { it == seen }.observed)
    assertEquals(setOf(seen, hunger), state.keys())
  }

  @Test
  fun `keys of one name share an id, so either reads the value`() {
    val again = StateKey<Int>("worldStateTest.hunger")

    assertEquals(hunger.id, again.id)
    assertEquals(4, WorldState.of(hunger to 4).get(again))
  }
}
