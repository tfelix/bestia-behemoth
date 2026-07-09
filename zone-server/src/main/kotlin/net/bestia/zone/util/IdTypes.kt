package net.bestia.zone.util

import net.bestia.zone.ecs.core.Long2IntOpenHashMap

typealias PlayerBestiaId = Long

/**
 * Entity identifier. A plain [Long] so it is compatible with the existing
 * snowflake `net.bestia.zone.util.EntityId` space of the project. The ECS treats
 * ids as opaque longs, which is why the sparse index is backed by
 * [Long2IntOpenHashMap] instead of a plain array.
 */
typealias EntityId = Long

typealias MasterEntityId = Long
typealias AccountId = Long
