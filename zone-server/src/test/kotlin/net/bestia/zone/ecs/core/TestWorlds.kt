package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId
import java.util.concurrent.atomic.AtomicLong

/**
 * Deterministic, unbounded [EntityIdGenerator] for tests: hands out sequential ids from [start].
 * Unlike [SnowflakeEntityIdGenerator] it has no per-millisecond cap, so tests that create many
 * thousands of entities in a tight loop stay reliable, and the ids stay small and predictable.
 */
class SequentialEntityIdGenerator(start: Long = 1L) : EntityIdGenerator {
  private val next = AtomicLong(start)
  override fun nextId(): EntityId = next.getAndIncrement()
}

/**
 * Builds a [World] wired for tests: sequential entity ids, plus the given [systems] and wave mode.
 * Mirrors the production `World(idGenerator, systems)` construction while keeping test call sites
 * terse now that systems are supplied at construction (there is no more `addSystem`).
 */
fun testWorld(
  parallelSystems: Boolean = false,
  systems: Iterable<System> = emptyList(),
): World = World(
  parallelSystems = parallelSystems,
  idGenerator = SequentialEntityIdGenerator(),
  systems = systems,
)
