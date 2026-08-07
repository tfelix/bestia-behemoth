package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.util.EntityId

/**
 * Uses a real skill on [targetId] through [skills], the one place in the codebase where a skill
 * actually takes effect.
 *
 * This replaces the old melee leaf, which stacked a `Damage` component carrying `Random.nextInt(1, 3)`
 * straight onto the target — bypassing the skill catalogue, `KnownSkills`, mana, range, line of sight
 * and every strategy script. A mob now attacks by the same route a player does, so a skill only has to
 * be balanced once.
 *
 * The skill's own cast time, range and mana are enforced downstream by `SkillExecutionService`; rate
 * limiting belongs to the tree, so wrap this in a `cooldown { }` rather than tracking a timer here.
 *
 * SUCCESS once the skill has been handed off, FAILURE when the target is gone or the caster does not
 * know the skill — both of which the planner needs to hear so it can replan rather than keep swinging
 * at nothing.
 */
class UseSkill(
  private val targetId: EntityId,
  private val skillId: Long,
  private val skills: SkillExecutionService,
) : BtNode {

  override fun tick(context: BtContext): Status {
    val world = context.world
    if (!world.isAlive(targetId)) return Status.FAILURE

    // `levelOf` reports 0 for a skill the entity does not know, so 0 and a missing component are the
    // same refusal: this mob cannot cast this.
    val level = world.get(context.entityId, KnownSkills::class)?.levelOf(skillId) ?: 0
    if (level <= 0) return Status.FAILURE

    skills.execute(
      world = world,
      casterId = context.entityId,
      skillId = skillId,
      skillLevel = level,
      targetEntityId = targetId,
      targetPosition = null,
    )

    return Status.SUCCESS
  }

  override fun toString(): String = "UseSkill(skill=$skillId, target=$targetId)"
}

/**
 * Stands still for [seconds] of simulated time, then succeeds — sleeping, grazing, anything whose
 * whole point is that it takes a while.
 *
 * Counting elapsed time rather than ticks keeps the duration honest across the different cadences the
 * act system can be driven at, including a test stepping the world by hand.
 */
class Wait(private val seconds: Float) : BtNode {

  init {
    require(seconds > 0f) { "Wait requires seconds > 0, got $seconds" }
  }

  private var elapsed = 0f

  override fun tick(context: BtContext): Status {
    elapsed += context.deltaTime
    return if (elapsed >= seconds) Status.SUCCESS else Status.RUNNING
  }

  override fun toString(): String = "Wait(${seconds}s)"
}
