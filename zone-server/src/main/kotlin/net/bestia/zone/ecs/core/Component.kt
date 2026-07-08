package net.bestia.zone.ecs.core

/**
 * Marker interface for all components. Components are passive data holders; all
 * gameplay logic lives in [Ecs2System]s. This mirrors the marker-interface
 * approach of the existing `net.bestia.zone.ecs.Component` so a later migration
 * stays mechanical.
 */
interface Component

/**
 * Entity identifier. A plain [Long] so it is compatible with the existing
 * snowflake `net.bestia.zone.util.EntityId` space of the project. The ECS treats
 * ids as opaque longs, which is why the sparse index is backed by
 * [Long2IntOpenHashMap] instead of a plain array.
 */
typealias EntityId = Long
