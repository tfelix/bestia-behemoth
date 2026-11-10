package net.bestia.zone.casting

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import net.bestia.zone.battle.attack.BattleAttack

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
    targetPosition: Vec3L?,
    cost: SkillLevelCost = SkillLevelCost.of(skill),
  ): SkillContext? {
    val usedAttack = BattleAttack.of(skill, skillLevel).copy(range = cost.rangeTiles, manaCost = cost.manaCost)

    val battle = battleContextFactory.create(world, casterId, usedAttack, targetEntityId, targetPosition)
      ?: enchantedGroundItemContext(world, casterId, usedAttack, targetEntityId)
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
      skillLevel = skillLevel,
      skill = skill
    )
  }

  /**
   * An item has no combat stats, so an enchanted one on the ground is aimed at as the ground it lies on. The target
   * id stays on the [SkillContext], which is how Dispel tells it from a plain ground cast.
   */
  private fun enchantedGroundItemContext(
    world: World,
    casterId: EntityId,
    usedAttack: BattleAttack,
    targetEntityId: EntityId?,
  ): BattleContext? {
    val item = targetEntityId?.takeIf { world.has(it, ItemEnchantments::class) } ?: return null
    val lyingAt = world.get(item, Position::class)?.toVec3L() ?: return null

    return battleContextFactory.create(world, casterId, usedAttack, targetEntityId = null, targetPosition = lyingAt)
  }
}
