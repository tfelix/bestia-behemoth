package net.bestia.zone.casting

import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.battle.status.RemovalReason
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ActionGuard
import net.bestia.zone.battle.ActionReactions
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.Channel
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an activated skill: builds the context, runs the script, applies whatever number came back.
 * The single place a skill takes effect, whether it fired instantly or at the end of a channelled cast.
 *
 * ### Skills resolve on the tick thread, between ticks
 *
 * [execute] posts the resolution to the tick thread and returns. That is what lets a script query and spawn
 * and do relational work itself, instead of returning a spec for this service to enact. It runs between
 * ticks, where `World.iterating` is false, so structural changes apply immediately and a get-or-create on
 * the target's `IncomingDamage` component is atomic against every other caster. Casts resolve in the order they
 * were posted. [SkillBudget] bounds how long one cast may hold the tick.
 *
 * ### What is still checked here rather than in the script
 *
 * Mana, cooldown and liveness, because every skill pays them the same way. Everything else - who may be targeted,
 * what the skill does, what it leaves behind - is the script's.
 */
@Service
class SkillExecutionService(
  private val skillRepository: SkillRepository,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val skillContextFactory: SkillContextFactory,
  private val cooldowns: SkillCooldownService,
  private val actionGuard: ActionGuard = ActionGuard(),
  private val actionReactions: ActionReactions = ActionReactions.NONE,
  private val reagents: ReagentPayment? = null,
  private val statusEffects: StatusEffectService? = null,
  private val groundWards: GroundWards? = null,
) {

  /** Skills never change after the import, and resolution runs on the tick, so they are read once at boot. */
  private val skillCache = ConcurrentHashMap<Long, Skill>()

  /** Loads the whole skill catalogue; called at boot, after the importer. */
  fun warmUp() {
    skillRepository.findAll().forEach { skillCache[it.id] = it }
  }

  /** The catalogue row for [skillId], from memory, or null for an id the catalogue does not know. */
  fun skillOf(skillId: Long): Skill? {
    return skillCache[skillId]
  }

  /** Whether [casterId] may use [skillId] now; false for a skill the catalogue does not know. */
  fun isReady(world: World, casterId: EntityId, skillId: Long): Boolean {
    val skill = skillOf(skillId) ?: return false

    return cooldowns.isReady(world, casterId, skill.id)
  }

  /** Queues [skillId] for resolution on the tick thread and returns immediately, from any thread. */
  fun execute(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?,
    args: List<Long> = emptyList(),
  ) {
    world.post {
      resolve(world, casterId, skillId, skillLevel, targetEntityId, targetPosition, args)
    }
  }

  private fun resolve(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?,
    args: List<Long>,
  ) {
    val skill = skillOf(skillId)
    if (skill == null) {
      LOG.warn { "Skill $skillId activated by $casterId is not in the catalogue, ignoring" }
      return
    }

    // A cast that started before a stun is cancelled by it; this catches the AI, which casts without a bar.
    if (actionGuard.refuses(world, casterId, ActionLock.CAST)) {
      LOG.debug { "Skill $skillId by $casterId fizzled: the caster may not cast now" }
      return
    }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(skill)
    } catch (e: Exception) {
      LOG.warn(e) { "No usable strategy for skill $skillId (script=${skill.script}), ignoring activation" }
      return
    }

    if (targetEntityId != null && targetEntityId != casterId &&
      world.get(targetEntityId, StatusEffects::class)?.isConcealed() == true
    ) {
      LOG.debug { "Skill $skillId by $casterId fizzled: its target is hidden" }
      return
    }

    val cost = strategy.costOf(skill, skillLevel)
    val ritual = RitualBoost.heldBy(world, casterId)
    val ctx = skillContextFactory.create(
      world, casterId, skill, skillLevel, targetEntityId, targetPosition, cost, args, ritual
    )
    if (ctx == null) {
      LOG.debug { "Skill $skillId by $casterId fizzled: caster or target no longer resolvable" }
      return
    }

    if (!cooldowns.isReady(world, casterId, skill.id)) {
      LOG.debug { "Skill $skillId by $casterId fizzled: still cooling down" }
      return
    }

    try {
      // Checked here rather than at activation on purpose: for a channelled skill the caster may have
      // drifted out of range or lost line of sight while casting, which must make the skill fizzle.
      if (!strategy.isCastPossible(ctx)) {
        LOG.debug { "Skill $skillId by $casterId fizzled: the cast is no longer possible" }
        return
      }

      // A ground spell cast into a warded zone fails before anything is paid, unless its Potency wins.
      if (targetPosition != null && groundWards?.blocks(world, targetPosition, ctx.potency) == true) {
        LOG.debug { "Skill $skillId by $casterId fizzled: the ground at $targetPosition is warded" }
        return
      }

      // After the possibility check and before the effect, so a refused cast costs nothing and a
      // resolved one is always paid for.
      if (reagents?.pay(world, casterId, skill.mechanics.reagent) == false) {
        LOG.debug { "Skill $skillId by $casterId fizzled: it lacks ${skill.mechanics.reagent?.item}" }
        return
      }

      if (!ctx.world.payCasterCost(mana = cost.manaCost, stamina = cost.staminaCost)) {
        LOG.debug { "Skill $skillId by $casterId fizzled: not enough mana or stamina" }
        return
      }

      // Spent by the cast that is paid for, even if it misses or fails, as the ritual rules say.
      if (ritual != null) {
        statusEffects?.removeEffect(world, casterId, StatusEffectId.RITUAL_CHARGE.id, RemovalReason.CANCELLED)
      }

      // Before the effect lands, so what casting ends - a hiding, a sprint - ends before this cast begins, and a
      // skill that hides its own caster is not undone by its own cast.
      actionReactions.notifyActed(world, casterId, EntityAction.CAST)
      world.get(casterId, Facing::class)?.turnTowards(ctx.battle.attacker.position, ctx.aimedAt)
      val result = strategy.execute(ctx)
      cooldowns.start(world, casterId, skill.id, cost.cooldownSeconds)
      startChannel(world, casterId, skill, skillLevel, targetEntityId, targetPosition, args)

      // A skill whose whole effect is a patch of ground, a station or a chart has no number to show, and
      // a ground-targeted one has no entity to show it on.
      if (result != null && targetEntityId != null) {
        ctx.world.apply(targetEntityId, result)
      }

      LOG.trace { "Skill $skillId by $casterId resolved, spending ${cost.manaCost} mana, ${cost.staminaCost} stamina" }
    } catch (e: SkillBudgetExceededException) {
      // Not rolled back: the ECS has no transaction, so whatever the script already did stands. A script
      // that trips this has a bug, and the log is what surfaces it.
      LOG.error(e) { "Skill $skillId (script=${skill.script}) by $casterId overran its execution budget" }
    } catch (e: Exception) {
      LOG.error(e) { "Skill $skillId (script=${skill.script}) by $casterId failed" }
    }
  }

  /** Holds the caster in place while a channelled skill keeps acting, see [channelTick]. */
  private fun startChannel(
    world: World,
    casterId: EntityId,
    skill: Skill,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?,
    args: List<Long>,
  ) {
    val seconds = skill.mechanics.channelSeconds
    if (seconds <= 0f) return

    world.add(
      casterId,
      Channel(skill.id, skillLevel, targetEntityId, targetPosition, skill.mechanics.channelInterval, seconds, args)
    )
    statusEffects?.applyEffect(world, casterId, StatusEffectId.CHANNELING, skillLevel, durationSeconds = seconds.toDouble())
  }

  /** One beat of a channelled skill. A target that is gone ends the channel, as it would end a cast. */
  fun channelTick(world: World, casterId: EntityId, channel: Channel) {
    val skill = skillOf(channel.skillId) ?: return
    val strategy = skillStrategyFactory.getSkillStrategy(skill)
    val ctx = skillContextFactory.create(
      world, casterId, skill, channel.skillLevel, channel.targetEntityId, channel.targetPosition,
      strategy.costOf(skill, channel.skillLevel), channel.args
    ) ?: return

    try {
      strategy.onChannelTick(ctx, channel.ticks)
    } catch (e: SkillBudgetExceededException) {
      LOG.error(e) { "Channel of skill ${skill.id} by $casterId overran its execution budget" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
