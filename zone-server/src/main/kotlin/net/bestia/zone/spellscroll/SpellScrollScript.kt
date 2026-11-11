package net.bestia.zone.spellscroll

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.casting.SkillStrategyFactory
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A spell scroll read from the bag: it casts its attack once at the inscribed level, with the user's own stats and
 * no cost, aimed as the attack is - at a foe, a friend (the user when none was picked) or the ground. A cast that
 * cannot start keeps the scroll; one that starts uses it up, even when the attack then misses.
 */
@Component
class SpellScrollScript(
  private val scrolls: SpellScrollService,
  private val skillExecution: SkillExecutionService,
  private val skillStrategies: SkillStrategyFactory,
  private val out: OutMessageProcessor,
) : ItemScript {

  override val itemId: Long = ITEM_ID

  /** A scroll is always one instance, so it is only ever used through [executeHeld]. */
  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    return false
  }

  override fun executeHeld(world: World, userId: EntityId, args: ScriptArgs, uniqueId: Long): Boolean {
    val spell = scrolls.spellOn(uniqueId)
    val skill = spell?.let { skillExecution.skillOf(it.skillId) }?.takeIf(skillStrategies::isCastable)
    val owner = ItemOwner.of(world, userId)
    if (spell == null || skill == null || owner == null) {
      LOG.warn { "Entity $userId used spell scroll $uniqueId, which holds no attack it can cast" }
      return false
    }

    val target = targetOf(world, userId, skill.targetType, args) ?: return false
    val from = world.get(userId, Position::class)?.toVec3L() ?: return false
    val aimedAt = target.position ?: target.entityId?.let { world.get(it, Position::class)?.toVec3L() } ?: return false
    val reach = skillStrategies.getSkillStrategy(skill).costOf(skill, spell.level).rangeTiles
    // Checked here although the cast checks it again: a cast that fizzles out of reach would waste the scroll.
    if (from.distance(aimedAt) > reach) {
      return refuse(world, userId, OpError.SPELL_OUT_OF_RANGE)
    }

    skillExecution.executeFree(world, userId, skill.id, spell.level, target.entityId, target.position)
    scrolls.destroyUsed(owner.masterId, uniqueId)

    return true
  }

  /** Where the attack goes, as its target type asks; null when the user picked nothing it can go at. */
  private fun targetOf(world: World, userId: EntityId, targetType: SkillTargetType, args: ScriptArgs): Target? {
    val pickedId = args.long(ScriptArgKeys.TARGET_ENTITY_ID)?.takeIf { it != 0L }

    return when (targetType) {
      SkillTargetType.GROUND, SkillTargetType.AOE_GROUND -> args.vec(ScriptArgKeys.POSITION)?.let { Target(null, it) }
      SkillTargetType.FRIENDLY -> Target(pickedId ?: userId, null)
      SkillTargetType.ENEMY -> {
        val foeId = pickedId ?: return null
        when (DamageGate.verdict(world, userId, foeId)) {
          DamageGate.Verdict.ADMITTED -> Target(foeId, null)
          DamageGate.Verdict.WARDED -> refused(world, userId, OpError.COMBAT_TARGET_WARDED)
          else -> refused(world, userId, OpError.REQUEST_REFUSED)
        }
      }
    }
  }

  private fun refused(world: World, userId: EntityId, code: OpError): Target? {
    refuse(world, userId, code)

    return null
  }

  private fun refuse(world: World, userId: EntityId, code: OpError): Boolean {
    world.get(userId, Account::class)?.let { out.sendToPlayer(it.accountId, OperationErrorSMSG(code)) }

    return false
  }

  /** An entity for an attack aimed at one, else a spot on the ground. */
  private class Target(val entityId: EntityId?, val position: Vec3L?)

  companion object {
    /** `spell_scroll` in items.yml. */
    const val ITEM_ID = 338L

    private val LOG = KotlinLogging.logger { }
  }
}
