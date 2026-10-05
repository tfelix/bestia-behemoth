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

/** Builds an [EcsWorld] wired for tests: sequential entity ids, plus the given [systems] and wave mode. */
fun testWorld(
  parallelSystems: Boolean = false,
  systems: Iterable<System> = emptyList(),
): EcsWorld = EcsWorld(
  parallelSystems = parallelSystems,
  idGenerator = SequentialEntityIdGenerator(),
  systems = systems,
  undeclaredAccess = UndeclaredAccess.FAIL,
)
