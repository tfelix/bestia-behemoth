package net.bestia.zone.battle.skill

/**
 * How a skill is aimed by the caster. Mirrored client-side by AttackResource.target_type
 * (bestia-client/src/Game/Attack/attack_resource.gd) via the target_type/aoe_radius sync in
 * buildSrc/src/main/kotlin/SkillDbSyncTask.kt - keep the spelled-out names identical between the
 * two, they're matched by string, not ordinal.
 */
enum class SkillTargetType {
  /**
   * A single ground point, no radius.
   */
  GROUND,

  /**
   * A ground point with an area of effect - see Skill.aoeRadius.
   */
  AOE_GROUND,

  /**
   * A single entity target that defaults to a hostile entity.
   */
  ENEMY,

  /**
   * A single entity target that defaults to a friendly entity.
   */
  FRIENDLY
}
