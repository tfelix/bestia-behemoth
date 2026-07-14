package net.bestia.zone.ecs.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tuning for the periodic [EntityPersistenceService]. Picked up automatically by the app-level
 * `@ConfigurationPropertiesScan`; all values have sane defaults so no config is required.
 */
@ConfigurationProperties(prefix = "persistence")
class EntityPersistenceConfig(
  /** How often a full persistence sync runs. Kept in sync with the `@Scheduled` default below. */
  val intervalMs: Long = 90_000,
  /** Delay before the first sync after startup. */
  val initialDelayMs: Long = 90_000,
  /** How many entities are snapshotted+written per batch, to bound lock-hold time and DB pressure. */
  val batchSize: Int = 200,
)
