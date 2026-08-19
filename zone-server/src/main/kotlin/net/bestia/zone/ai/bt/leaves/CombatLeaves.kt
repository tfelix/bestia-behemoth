package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.util.EntityId

/**
 * Casts a catalogued skill on [targetId] through [skills]. For a plain bite or swing use [BasicAttack]
 * instead — a basic attack is not a catalogue row and needs neither `KnownSkills` nor a script.
 *
 * The skill's own range and mana are enforced downstream by `SkillExecutionService`; rate limiting belongs
 * to the tree, so wrap this in a `cooldown { }` rather than tracking a timer here.
 *
 * SUCCESS once the skill has been handed off, FAILURE when the target is gone or the caster does not know
 * the skill — both of which the planner needs to hear so it can replan rather than keep swinging at
 * nothing.
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
 * Swings at [targetId] with the attack an entity has when it has nothing else: no catalogue row, no script,
 * no mana, no cast bar.
 *
 * Separate from [UseSkill] because the two share nothing but the word "attack". This is also what mobs use,
 * which is why nothing seeds them a skill id any more — the old arrangement had them casting id 0, a row that
 * is not in `skills.yml` and never was.
 *
 * Resolves inline rather than off-thread: a swing does no world manipulation beyond staging the damage.
 */
class BasicAttack(
  private val targetId: EntityId,
  private val attacks: AttackExecutionService,
) : BtNode {

  override fun tick(context: BtContext): Status {
    val world = context.world
    if (!world.isAlive(targetId)) return Status.FAILURE

    // TODO Take the weapon and its element off the attacker once an equipment system exists.
    attacks.attack(world, context.entityId, targetId, BattleAttack.getBasicMeleeAttack(Element.NORMAL))

    return Status.SUCCESS
  }

  override fun toString(): String = "BasicAttack(target=$targetId)"
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
