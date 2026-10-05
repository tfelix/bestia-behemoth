package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Builds the [SkillContext] one cast runs against: a snapshot of the fight plus a fresh budget.
 *
 * The snapshot is taken inside a single world scope and is a plain value afterwards, which is what lets
 * the script's checks be pure functions of it. [BattleContextFactory] does that part - it is the one
 * place ECS state is projected onto [net.bestia.zone.battle.BattleEntity], and duplicating it here
 * would give the skill pathway a second copy of the defence and status formulas to drift from.
 */
@Component
class SkillContextFactory(
  private val battleContextFactory: BattleContextFactory,
  private val skillWorldServices: SkillWorldServices,
  private val config: SkillExecutionConfig,
) {

  /**
   * Null when the cast has nothing left to resolve against: a caster or target that died or despawned,
   * or a ground cast with no position.
   */
  fun create(
    world: World,
    casterId: EntityId,
    skill: Skill,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ): SkillContext? {
    val usedAttack = BattleAttack.of(skill, skillLevel)

    val battle = battleContextFactory.create(world, casterId, usedAttack, targetEntityId, targetPosition)
      ?: return null

    val budget = SkillBudget(
      maxOps = config.worldOpsPerCast,
      maxQueryResults = config.maxQueryResults,
      maxMillis = config.maxMillisPerCast
    )

    return SkillContext(
      battle = battle,
      world = BudgetedSkillWorld(
        world = world,
        budget = budget,
        services = skillWorldServices,
        casterId = casterId,
        skillId = skill.id,
        skillLevel = skillLevel
      ),
      casterId = casterId,
      targetEntityId = targetEntityId,
      targetPosition = targetPosition,
      skillId = skill.id,
      skillLevel = skillLevel
    )
  }
}
