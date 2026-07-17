package net.bestia.zone.battle.status

/**
 * Static, in-memory definition of a status effect (buff/debuff): what it's called, how it scales with level, how
 * re-application behaves, and what it does while active. Loaded from `status_effects.yml` at boot by
 * [net.bestia.zone.boot.StatusEffectImporterBootRunner] into [StatusEffectDefinitionRegistry] - this is config,
 * not player state, so it is never persisted to the database (same shape as
 * `net.bestia.zone.battle.skill.MasterSkillTreeNode`).
 */
data class StatusEffectDefinition(
  val id: Long,
  val identifier: String,
  val polarity: StatusEffectSource,
  val showIcon: Boolean,
  val baseDurationSeconds: Double,
  val durationPerLevel: Double = 0.0,
  val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
  val effects: List<StatusEffect> = emptyList()
) {
  fun durationSeconds(level: Int): Double {
    return baseDurationSeconds + durationPerLevel * (level - 1)
  }
}
