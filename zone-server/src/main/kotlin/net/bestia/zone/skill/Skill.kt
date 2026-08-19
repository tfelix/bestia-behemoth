package net.bestia.zone.skill

import jakarta.persistence.*
import net.bestia.zone.battle.skill.SkillTargetType
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
  var strength: Int?,

  /**
   * Names the `SkillStrategy` that implements this skill, and so decides whether it can be *cast* at all -
   * see `SkillStrategyFactory.isCastable`. Null means it cannot be: a passive, or a skill nobody has written
   * yet. There is deliberately no `type` column saying so; one enum meaning both "how is damage calculated"
   * and "may this be cast" is what the skill/attack split was undoing.
   *
   * A passive with a stat effect leaves this null too - its `PassiveSkillScript` names the skill rather than
   * the other way round, which keeps this column resolving into exactly one bean registry.
   */
  @Column(nullable = true)
  var script: String?,

  @Column(nullable = false)
  var manaCost: Int,

  // Named explicitly: `range` is a reserved word in MariaDB (window-function frame syntax), so an
  // unquoted `create table` with a bare `range` column fails on boot. H2 never enforced this.
  @Column(name = "attack_range", nullable = true)
  var range: Int?,

  @Column(nullable = false)
  var targetType: SkillTargetType,

  @Column(nullable = true)
  var aoeRadius: Double? = null,

  var needsLineOfSight: Boolean,

  /**
   * Seconds the caster must channel before the skill resolves. 0 means it resolves instantly.
   * While casting the entity carries a `Casting` component; moving cancels it.
   */
  @Column(nullable = false)
  var castTime: Float = 0f,

  /**
   * 0 means this skill is immediately learnable.
   */
  var requiredLevel: Int,

  /**
   * Long-form BBCode flavor text, English only. Synced to the client's translation CSV by
   * `./gradlew syncSkillDb` - see `.claude/skills/skill-system/SKILL.md`.
   */
  @Column(columnDefinition = "TEXT", nullable = true)
  var description: String? = null
) {

  init {
    requireValidIdentifier(identifier)
    validate()
  }

  /**
   * Copies every content field off [other], returning whether anything actually differed.
   *
   * `id` and `identifier` are the match key the importer resolved [other] by and are deliberately
   * not copied. [other] validated itself on construction, so no re-check is needed here.
   */
  fun updateContentFrom(other: Skill): Boolean {
    val changed = strength != other.strength ||
        script != other.script ||
        manaCost != other.manaCost ||
        range != other.range ||
        targetType != other.targetType ||
        aoeRadius != other.aoeRadius ||
        needsLineOfSight != other.needsLineOfSight ||
        castTime != other.castTime ||
        requiredLevel != other.requiredLevel ||
        description != other.description

    if (!changed) {
      return false
    }

    strength = other.strength
    script = other.script
    manaCost = other.manaCost
    range = other.range
    targetType = other.targetType
    aoeRadius = other.aoeRadius
    needsLineOfSight = other.needsLineOfSight
    castTime = other.castTime
    requiredLevel = other.requiredLevel
    description = other.description

    return true
  }

  private fun validate() {
    require(requiredLevel >= 0) {
      "requiredLevel must be >= 0"
    }

    require(castTime >= 0f) {
      "Skill $identifier: castTime must be >= 0"
    }

    val radius = aoeRadius
    require((targetType == SkillTargetType.AOE_GROUND) == (radius != null && radius > 0.0)) {
      "Skill $identifier: aoeRadius must be set (>0) iff targetType is AOE_GROUND"
    }
  }
}
