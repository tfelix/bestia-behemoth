package net.bestia.zone.battle.status

/**
 * Static, in-memory definition of a status effect (buff/debuff): what it's called, whether the
 * client is told about it, and which [StatusEffectScript] implements its behavior (duration,
 * stacking, and stat modification). Loaded from `status_effects.yml` at boot by
 * [net.bestia.zone.boot.StatusEffectImporterBootRunner] into [StatusEffectDefinitionRegistry] -
 * this is config, not player state, so it is never persisted to the database.
 */
data class StatusEffectDefinition(
  val id: Long,
  val identifier: String,
  val isSyncedToClient: Boolean,
  val script: String,
)
