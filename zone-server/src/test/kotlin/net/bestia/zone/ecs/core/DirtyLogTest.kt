package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class DirtyLogTest {

  private class Tracked : DirtyableComponent() {
    override fun toEntityMessage(entityId: Long, removed: Boolean) = error("not sent in this test")
    override fun syncTargets(world: World, entityId: EntityId) = error("not sent in this test")
  }

  private val world = testWorld()

  private fun drained(): List<Pair<EntityId, KClass<out Component>>> {
    val out = mutableListOf<Pair<EntityId, KClass<out Component>>>()
    world.dirtyLog.drainDirtied { id, type -> out.add(id to type) }

    return out
  }

  @Test
  fun `a fresh component is logged once when it is added`() {
    val e = world.create()
    world.add(e, Tracked())

    assertEquals(listOf(e to Tracked::class), drained())
    assertEquals(emptyList(), drained(), "a drain forgets what it handed out")
  }

  @Test
  fun `dirtying an already dirty component logs nothing more`() {
    val e = world.create()
    val c = world.add(e, Tracked())
    drained()

    c.markDirty()

    assertEquals(emptyList(), drained())
  }

  @Test
  fun `a cleared component is logged again on its next change`() {
    val e = world.create()
    val c = world.add(e, Tracked())
    drained()
    c.clearDirty()

    c.markDirty()
    c.markDirty()

    assertEquals(listOf(e to Tracked::class), drained())
  }

  @Test
  fun `a removed component no longer reports`() {
    val e = world.create()
    val c = world.add(e, Tracked())
    drained()
    c.clearDirty()

    world.remove(e, Tracked::class)
    c.markDirty()

    assertEquals(emptyList(), drained())
  }

  @Test
  fun `an instance moved to another entity reports for the new one`() {
    val from = world.create()
    val to = world.create()
    val c = world.add(from, Tracked())
    drained()
    c.clearDirty()

    world.add(to, c)
    world.remove(from, Tracked::class)
    c.markDirty()

    assertEquals(listOf(to to Tracked::class), drained())
  }

  @Test
  fun `a replaced component stops reporting, its replacement starts`() {
    val e = world.create()
    val old = world.add(e, Tracked())
    drained()
    old.clearDirty()

    world.add(e, Tracked())
    old.markDirty()

    assertEquals(listOf(e to Tracked::class), drained(), "only the replacement, once, from being added")
  }
}
