package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * Everything a [SkillStrategy] is given: an immutable snapshot of the fight, and a budgeted door to the
 * live world.
 *
 * [battle] is taken under one lock scope before the script runs and is a plain value from then on, so
 * range checks and damage formulas are pure functions of it and can be unit-tested without a world. It
 * is also, necessarily, *stale*: the script runs off the tick thread, so anything it acts on must be
 * re-checked through [world], which returns null or false for whatever has since died.
 */
class SkillContext(
  val battle: BattleContext,
  val world: SkillWorld,
  val casterId: EntityId,
  val targetEntityId: EntityId?,
  val targetPosition: Vec3L?,
  val skillId: Long,
  val skillLevel: Int,
) {

  /** Where the skill was aimed: the target's position for an entity cast, the aimed-at point otherwise. */
  val aimedAt: Vec3L get() = battle.targetPosition()

  fun requireGroundContext(): GroundBattleContext = BattleContext.verifyGroundBattleContext(battle)

  /**
   * Puts [effect] on [targetEntityId] at this cast's level, which is what almost every buff wants - a script
   * that needs a different level calls [SkillWorld.applyStatusEffect] with it.
   */
  fun applyStatusEffect(targetEntityId: EntityId, effect: StatusEffectId) {
    world.applyStatusEffect(targetEntityId, effect.id, skillLevel)
  }
}
