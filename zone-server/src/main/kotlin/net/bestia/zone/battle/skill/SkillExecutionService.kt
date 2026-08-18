package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.AreaEffectResult
import net.bestia.zone.battle.damage.Buff
import net.bestia.zone.battle.damage.CraftingResult
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.damage.SurveyResult
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.ecs.battle.effects.AreaEffectSpawner
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.skill.findByIdOrThrow
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import net.bestia.zone.battle.damage.Damage as DamageResult

/**
 * Resolves an activated skill: builds the battle context, picks the strategy, runs it and applies the
 * result. This is the single place where a skill actually takes effect, whether it fired instantly
 * (no cast time) or at the end of a channelled cast.
 *
 * Runs with the world lock held (the lock is reentrant, so both the tick thread and a message handler
 * inside a `modify` block can call in).
 */
@Service
class SkillExecutionService(
  private val skillRepository: SkillRepository,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val outMessageProcessor: OutMessageProcessor,
  private val statusEffectService: StatusEffectService,
) {

  /**
   * Skills are immutable once imported, so they are cached rather than hitting JPA on every cast -
   * this runs on the tick thread under the world lock, where a database round trip would stall the
   * whole simulation.
   */
  private val skillCache = ConcurrentHashMap<Long, Skill>()

  fun execute(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ) {
    // TODO check if the entity owns the requested skill in this level?

    val skill = skillCache.computeIfAbsent(skillId) { skillRepository.findByIdOrThrow(it) }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(skill)
    } catch (e: Exception) {
      LOG.warn(e) { "No usable strategy for skill $skillId (script=${skill.script}), ignoring activation" }
      return
    }

    // Checked here rather than at activation on purpose: for a channelled skill the caster may have
    // drifted out of range or lost line of sight while casting, which must make the skill fizzle.
    if (!strategy.isAttackPossible(ctx)) {
      LOG.debug { "Skill $skillId by $casterId fizzled: attack not possible (range/line of sight)" }
      return
    }

    if (!consumeMana(world, casterId, skill.manaCost)) {
      LOG.debug { "Skill $skillId by $casterId fizzled: not enough mana" }
      return
    }

    applyResult(world, casterId, skillId, skillLevel, targetEntityId, targetPosition, strategy.execute(ctx))

    // After the result, so a skill that marks its target only does so once the cast really resolved.
    targetEntityId?.let { target ->
      strategy.effectsOnTarget(ctx).forEach { effect ->
        statusEffectService.applyEffect(world, target, effect, skillLevel, casterId)
      }
    }
  }

  /** Returns false (spending nothing) when the caster cannot pay. */
  private fun consumeMana(world: World, casterId: EntityId, manaCost: Int): Boolean {
    if (manaCost <= 0) {
      return true
    }

    val mana = world.get(casterId, Mana::class) ?: return true
    if (mana.current < manaCost) {
      return false
    }

    mana.current -= manaCost
    return true
  }

  private fun applyResult(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?,
    result: DamageResult
  ) {

    // Every other result is a number aimed at one entity, which a ground-targeted skill does not have.
    val targetId = targetEntityId ?: return

    val type = when (result) {
      is Miss -> DamageEntitySMSG.DamageType.MISS
      is CriticalHit -> DamageEntitySMSG.DamageType.CRIT
      is Heal -> DamageEntitySMSG.DamageType.HEAL
      is HitDamage, is TrueDamage -> DamageEntitySMSG.DamageType.NORMAL
    }

    val position = world.get(casterId, Position::class)?.toVec3L() ?: return
    val msg = DamageEntitySMSG(
      entityId = targetId,
      sourceEntityId = casterId,
      attackId = skillId.toInt(),
      div = 1,
      damage = result.amount,
      skillLevel = skillLevel,
      type = type
    )

    // Deferred for two reasons: the network fan-out (an AOI query plus writes) should not happen in
    // the middle of a system, and `World.add` is itself deferred while a system iterates - so
    // staging damage inline would make two casts landing on the same target in the same tick each
    // create their own Damage component, the second silently replacing the first. Inside a deferred
    // block structural changes are applied immediately, so the get-or-create below is sound.
    world.defer {
      when (result) {
        is Miss -> Unit

        // CurMax.current clamps to [0, max] itself.
        is Heal -> world.get(targetId, Health::class)?.let { it.current += result.amount }

        // Damage is staged on the target as a component; ReceivedDamageSystem drains it into Health,
        // which also handles death, threat tracking and interrupting the victim's own cast.
        else -> {
          val damage = world.get(targetId, DamageComponent::class) ?: world.add(targetId, DamageComponent())
          damage.add(result.amount, casterId)
        }
      }

      outMessageProcessor.sendToAllPlayersInRange(position, msg)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
