package net.bestia.zone.ecs.core

import kotlin.reflect.KClass

/**
 * A unit of gameplay logic. Implementations are typically Spring `@Component`
 * beans; they are collected into the [World] via `List<Ecs2System>` injection
 * (see `Ecs2Configuration`).
 *
 * A system declares:
 *  - its [schedule] (how often it runs), and
 *  - the component types it [reads] and [writes].
 *
 * The [SystemScheduler] uses the read/write sets to run *non-conflicting*
 * systems in parallel. Two systems conflict when one writes a component type the
 * other reads or writes; conflicting systems are never run concurrently and keep
 * their registration order. Declaring these sets accurately is what makes safe
 * multithreading possible — leave them empty only for systems that touch no
 * shared component state.
 */
interface System {
  val schedule: Schedule
    get() = Schedule.EveryTick

  val reads: Set<KClass<out Component>>
    get() = emptySet()

  val writes: Set<KClass<out Component>>
    get() = emptySet()

  val name: String
    get() = this::class.simpleName ?: "AnonymousSystem"

  fun update(world: World, deltaTime: Float)
}
