package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickOrderTest {

  private class Position : Component
  private class Velocity : Component

  private abstract class TestSystem(
    override val phase: Phase,
    override val reads: ComponentClassSet = emptySet(),
    override val writes: ComponentClassSet = emptySet(),
    override val after: Set<KClass<out System>> = emptySet(),
  ) : System {
    override fun update(world: World, deltaTime: Float) {}
  }

  private class Steer(after: Set<KClass<out System>> = emptySet()) :
    TestSystem(Phase.MOVEMENT, writes = setOf(Velocity::class), after = after)

  private class Move(after: Set<KClass<out System>> = emptySet()) :
    TestSystem(Phase.MOVEMENT, reads = setOf(Velocity::class), writes = setOf(Position::class), after = after)

  private class Think : TestSystem(Phase.AI, reads = setOf(Position::class))
  private class Persist : TestSystem(Phase.PERSIST, reads = setOf(Position::class))
  private class Late(after: Set<KClass<out System>>) : TestSystem(Phase.AI, after = after)

  @Test
  fun `phases run in their declared order whatever the registration order`() {
    val order = TickOrder.of(listOf(Persist(), Move(after = setOf(Steer::class)), Think(), Steer()))

    assertEquals(listOf("Think", "Steer", "Move", "Persist"), order.map { it.name })
  }

  @Test
  fun `after orders two conflicting systems of one phase`() {
    val order = TickOrder.of(listOf(Move(after = setOf(Steer::class)), Steer()))

    assertEquals(listOf("Steer", "Move"), order.map { it.name })
  }

  @Test
  fun `two conflicting systems of one phase without after are refused`() {
    val failure = assertThrows<IllegalStateException> { TickOrder.of(listOf(Move(), Steer())) }

    assertTrue("Velocity" in failure.message!!, failure.message)
  }

  @Test
  fun `running after a system of a later phase is refused`() {
    assertThrows<IllegalStateException> { TickOrder.of(listOf(Late(after = setOf(Persist::class)), Persist())) }
  }

  @Test
  fun `systems that wait for each other are refused`() {
    assertThrows<IllegalStateException> {
      TickOrder.of(listOf(Move(after = setOf(Steer::class)), Steer(after = setOf(Move::class))))
    }
  }
}
