package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class Pos(var x: Int = 0, var y: Int = 0) : Component

class ComponentStoreTest {

  @Test
  fun `set get has and replace`() {
    val store = ComponentStore(Pos::class)
    val p = Pos(1, 2)
    store.set(10L, p)

    assertTrue(store.has(10L))
    assertSame(p, store.get(10L))
    assertEquals(1, store.size)

    val p2 = Pos(3, 4)
    store.set(10L, p2)
    assertSame(p2, store.get(10L))
    assertEquals(1, store.size, "replace must not grow the store")
  }

  @Test
  fun `swap-remove keeps the store packed and other entities reachable`() {
    val store = ComponentStore(Pos::class)
    for (i in 1..5) store.set(i.toLong(), Pos(i, i))

    // remove from the middle -> last element gets swapped into the hole
    store.remove(2L)

    assertFalse(store.has(2L))
    assertEquals(4, store.size)
    for (i in intArrayOf(1, 3, 4, 5)) {
      assertTrue(store.has(i.toLong()))
      assertEquals(i, store.get(i.toLong())!!.x)
    }

    // full iteration should visit exactly the survivors
    val seen = mutableSetOf<Long>()
    store.each { id, _ -> seen.add(id) }
    assertEquals(setOf(1L, 3L, 4L, 5L), seen)
  }

  @Test
  fun `pooled store recycles instances via obtain`() {
    val store = ComponentStore(Pos::class, factory = { Pos() }, reset = { it.x = 0; it.y = 0 })
    val first = store.obtain(1L)
    first.x = 99
    store.remove(1L) // recycles `first` back into the pool (and resets it)

    val second = store.obtain(2L)
    assertSame(first, second, "obtain should hand back the pooled instance")
    assertEquals(0, second.x, "reset should have cleared the recycled instance")
  }

  @Test
  fun `removing an absent entity returns null`() {
    val store = ComponentStore(Pos::class)
    assertNull(store.remove(123L))
  }
}
