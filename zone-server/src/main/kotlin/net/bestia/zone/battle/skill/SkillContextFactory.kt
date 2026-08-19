package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Builds the [SkillContext] one cast runs against: a snapshot of the fight plus a fresh budget.
 *
 * The snapshot is taken inside a single lock scope and is a plain value afterwards, which is what lets
 * the rest of the cast run off the tick thread. [BattleContextFactory] does that part - it is the one
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
    world: WorldView,
    casterId: EntityId,
    skill: Skill,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ): SkillContext? {
    val usedAttack = BattleAttack.of(skill, skillLevel)

    val battle = world.read {
      battleContextFactory.create(this, casterId, usedAttack, targetEntityId, targetPosition)
    } ?: return null

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
