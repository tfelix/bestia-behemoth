package net.bestia.zone.ecs.core

import kotlin.reflect.KClass

/**
 * A unit of gameplay logic, as a Spring bean. A system declares:
 *  - its [schedule] (how often it runs),
 *  - its [phase], and which systems of that phase it runs [after] or [before] (see [TickOrder]), and
 *  - the component types it [reads] and [writes].
 *
 * Two systems conflict when one writes a component type the other reads or writes. The [SystemScheduler] runs
 * conflicting systems one after the other, and [TickOrder] refuses two conflicting systems of one phase that
 * [after] does not order. With `world.undeclared-access` on, touching an undeclared type fails the system.
 */
interface System {
  val schedule: Schedule
    get() = Schedule.EveryTick

  val phase: Phase

  /** Systems of the same [phase] that must run before this one. */
  val after: Set<KClass<out System>>
    get() = emptySet()

  /**
   * Systems of the same [phase] that must run after this one. Means the same as their [after]. Declare it here
   * when those systems are in a lower slice and may not name this one.
   */
  val before: Set<KClass<out System>>
    get() = emptySet()

  val reads: ComponentClassSet
    get() = emptySet()

  val writes: ComponentClassSet
    get() = emptySet()

  val name: String
    get() = this::class.simpleName ?: "AnonymousSystem"

  fun update(world: World, deltaTime: Float)
}

typealias ComponentClassSet = Set<KClass<out Component>>