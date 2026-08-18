package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.AreaEffectResult
import net.bestia.zone.battle.damage.Buff
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.ecs.battle.effects.AreaEffect
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
import net.bestia.zone.skill.findByIdOrThrow
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
  private val battleContextFactory: BattleContextFactory,
  private val outMessageProcessor: OutMessageProcessor,
  private val statusEffectService: StatusEffectService,
  private val areaEffectSpawner: AreaEffectSpawner,
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
    val skill = skillCache.computeIfAbsent(skillId) { skillRepository.findByIdOrThrow(it) }
    val usedAttack = BattleSkill(skill, level = skillLevel)

    val ctx = battleContextFactory.create(world, casterId, usedAttack, targetEntityId, targetPosition)
    if (ctx == null) {
      LOG.debug { "Skill $skillId by $casterId fizzled: caster or target no longer resolvable" }
      return
    }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(ctx)
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

    if (!consumeMana(world, casterId, usedAttack.manaCost)) {
      LOG.debug { "Skill $skillId by $casterId fizzled: not enough mana" }
      return
    }

    applyResult(world, casterId, skillId, skillLevel, targetEntityId, targetPosition, strategy.doAttack(ctx))
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
    if (result is AreaEffectResult) {
      applyAreaEffect(world, casterId, skillId, skillLevel, targetEntityId, targetPosition, result)
      return
    }

    // Every other result is a number aimed at one entity, which a ground-targeted skill does not have.
    val targetId = targetEntityId ?: return

    // A buff has no health delta to broadcast as a DamageEntitySMSG - the client learns about it
    // via the StatusEffects component's own dirty-sync (StatusEffectsComponentSMSG) instead.
    if (result is Buff) {
      statusEffectService.applyEffect(world, targetId, result.effectId, skillLevel, casterId)
      return
    }

    val type = when (result) {
      is Miss -> DamageEntitySMSG.DamageType.MISS
      is CriticalHit -> DamageEntitySMSG.DamageType.CRIT
      is Heal -> DamageEntitySMSG.DamageType.HEAL
      is HitDamage, is TrueDamage -> DamageEntitySMSG.DamageType.NORMAL
      // Both unreachable (handled above); they keep the `when` exhaustive so a new result type is a
      // compile error here rather than a silently unhandled skill.
      is Buff, is AreaEffectResult -> return
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

  /**
   * Drops the patch at the aimed-at point, falling back to the target entity's own position so an
   * entity-targeted area skill still lands somewhere sensible.
   */
  private fun applyAreaEffect(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?,
    result: AreaEffectResult
  ) {
    val center = targetPosition
      ?: targetEntityId?.let { world.get(it, Position::class)?.toVec3L() }
      ?: run {
        LOG.debug { "Skill $skillId by $casterId fizzled: an area effect with nowhere to land" }
        return
      }

    val effect = AreaEffect.lasting(
      casterId = casterId,
      skillId = skillId,
      skillLevel = skillLevel,
      radiusTiles = result.radiusTiles,
      damagePerTick = result.damagePerTick,
      tickIntervalSeconds = result.tickIntervalSeconds,
      durationSeconds = result.durationSeconds,
      hitsCaster = result.hitsCaster
    )

    // Deferred for the reason the damage staging below is: a system iterating the world cannot create
    // an entity inline, and inside a deferred block the structural changes apply immediately.
    world.defer { areaEffectSpawner.spawn(world, center, result.visualId, effect) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
