package net.bestia.zone.ecs.core

/**
 * What the [EcsWorld] does when a running system touches a component type it did not declare in its reads or
 * writes. The wave scheduler sees only the declarations, so an undeclared access is an ordering it cannot keep.
 */
enum class UndeclaredAccess {
  /** No check, and no cost: the production default. */
  OFF,

  LOG,

  /** Throws, which fails the system for that tick. For tests. */
  FAIL,
}
