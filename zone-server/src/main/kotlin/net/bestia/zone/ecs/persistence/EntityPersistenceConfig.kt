package net.bestia.zone.ecs.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tuning for the periodic save by [EntityPersistenceSystem]. Picked up automatically by the app-level
 * `@ConfigurationPropertiesScan`; all values have sane defaults so no config is required.
 */
@ConfigurationProperties(prefix = "persistence")
class EntityPersistenceConfig(
  /** How often each persistent entity is saved, if it changed. The population is spread over this time. */
  val intervalMs: Long = 90_000,
)
