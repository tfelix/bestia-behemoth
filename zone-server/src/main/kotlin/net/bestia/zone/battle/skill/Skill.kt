package net.bestia.zone.battle.skill

import jakarta.persistence.*
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "skill",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Skill(
  @Id
  var id: Long = 0,

  @Column(nullable = false)
  val identifier: String,

  @Column(nullable = true)
  val strength: Int?,

  @Column(nullable = false)
  val type: SkillType,

  @Column(nullable = true)
  val script: String?,

  @Column(nullable = false)
  val manaCost: Int,

  @Column(nullable = true)
  val range: Int?,

  @Column(nullable = false)
  val targetType: SkillTargetType,

  @Column(nullable = true)
  val aoeRadius: Double? = null,

  val needsLineOfSight: Boolean,

  /**
   * 0 means this skill is immediately learnable.
   */
  val requiredLevel: Int,

  /**
   * Long-form BBCode flavor text, English only. Synced to the client's translation CSV by
   * `./gradlew syncSkillDb` - see `.claude/skills/skill-system/SKILL.md`.
   */
  @Column(columnDefinition = "TEXT", nullable = true)
  val description: String? = null
) {

  init {
    requireValidIdentifier(identifier)

    require(requiredLevel >= 0) {
      "requiredLevel must be >= 0"
    }

    // No damage skills are required to have a script and strength set to null.
    if (type == SkillType.NO_DAMAGE) {
      requireNotNull(script) {
        "Skill $identifier is NO_DAMAGE and must have a script attached"
      }
      require(strength == null) {
        "Skill $identifier is NO_DAMAGE and must have strength set to null"
      }
    }

    val radius = aoeRadius
    require((targetType == SkillTargetType.AOE_GROUND) == (radius != null && radius > 0.0)) {
      "Skill $identifier: aoeRadius must be set (>0) iff targetType is AOE_GROUND"
    }
  }
}
