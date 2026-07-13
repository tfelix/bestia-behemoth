package net.bestia.zone.battle.buff

/**
 * Static, in-memory definition of a buff/debuff: what it's called, how it scales with level, how
 * re-application behaves, and what it does while active. Loaded from `buffs.yml` at boot by
 * [net.bestia.zone.boot.BuffImporterBootRunner] into [BuffDefinitionRegistry] - this is config,
 * not player state, so it is never persisted to the database (same shape as
 * `net.bestia.zone.battle.skill.MasterSkillTreeNode`).
 */
data class BuffDefinition(
  val id: Long,
  val identifier: String,
  val polarity: BuffPolarity,
  val showIcon: Boolean,
  val baseDurationSeconds: Double,
  val durationPerLevel: Double = 0.0,
  val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
  val effects: List<BuffEffect> = emptyList()
) {
  fun durationSeconds(level: Int): Double {
    return baseDurationSeconds + durationPerLevel * (level - 1)
  }
}
