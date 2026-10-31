package net.bestia.zone.casting

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId

/**
 * Builds the [SkillContext] a script test runs against, over a [RecordingSkillWorld].
 *
 * The caster and target ids are taken off the [BattleContext]'s own entities rather than passed separately, so
 * a script that reads `ctx.casterId` and one that reads `ctx.battle.attacker.id` cannot disagree.
 */
object SkillContextFixture {

  fun skillCtx(
    battle: BattleContext = BattleContextFixture.entityCtx(),
    world: RecordingSkillWorld = RecordingSkillWorld(),
    skillId: Long = 1L,
    skillLevel: Int = battle.usedAttack.level,
    targetPosition: Vec3L? = null,
    skill: Skill = skill(skillId),
  ): SkillContext {
    val targetEntityId: EntityId? = (battle as? EntityBattleContext)?.defender?.id

    return SkillContext(
      battle = battle,
      world = world,
      casterId = battle.attacker.id,
      targetEntityId = targetEntityId,
      // A ground cast is aimed at a point and has no target entity; the snapshot already carries where.
      targetPosition = targetPosition ?: battle.targetPosition().takeIf { targetEntityId == null },
      skillId = skillId,
      skillLevel = skillLevel,
      skill = skill
    )
  }

  fun skill(id: Long = 1L) = Skill(
    id = id,
    identifier = "TEST_SKILL",
    strength = null,
    script = null,
    manaCost = 0,
    range = 5,
    targetType = SkillTargetType.ENEMY,
    needsLineOfSight = false,
    requiredLevel = 0
  )
}
