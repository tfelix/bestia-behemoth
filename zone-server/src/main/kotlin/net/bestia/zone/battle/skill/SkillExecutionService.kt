package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContextFactory
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
  private val battleContextFactory: BattleContextFactory,
  private val outMessageProcessor: OutMessageProcessor,
  private val statusEffectService: StatusEffectService,
  private val areaEffectSpawner: AreaEffectSpawner,
  private val craftingService: CraftingService,
  private val playerStructureService: PlayerStructureService,
  private val surveyService: SurveyService,
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
    if (result is AreaEffectResult) {
      applyAreaEffect(world, casterId, skillId, skillLevel, targetEntityId, targetPosition, result)
      return
    }

    if (result is CraftingResult) {
      applyCrafting(world, casterId, skillId, targetPosition, result)
      return
    }

    if (result is SurveyResult) {
      applySurvey(world, casterId, targetPosition, result)
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
      // All unreachable (handled above); they keep the `when` exhaustive so a new result type is a
      // compile error here rather than a silently unhandled skill.
      is Buff, is AreaEffectResult, is CraftingResult, is SurveyResult -> return
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
   * Sends the survey off to be charted, and does no database work here.
   *
   * Everything a chart needs - the blank to consume, the instance to mint, the row to write - is relational, and
   * this runs under the world lock on `zone-tick` where that is forbidden. So the only work done here is
   * resolving the three ids off the live world, which is the part that *cannot* be done later: by the time an
   * async job runs, the caster may have logged out.
   */
  private fun applySurvey(world: World, casterId: EntityId, targetPosition: Vec3L?, result: SurveyResult) {
    val at = targetPosition ?: world.get(casterId, Position::class)?.toVec3L()
    if (at == null) {
      LOG.debug { "Survey by $casterId has no position to centre on" }
      return
    }

    val masterId = world.get(casterId, Master::class)?.masterId
    if (masterId == null) {
      LOG.debug { "Entity $casterId is not a master and cannot hold a chart" }
      return
    }

    surveyService.survey(
      world = world,
      masterId = masterId,
      accountId = world.get(casterId, Account::class)?.accountId,
      entityId = casterId,
      centre = at,
      radiusMetres = result.radiusMetres
    )
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

  /**
   * Resolves a crafting skill's activation against the world: put a station up, or offer what can be made here.
   *
   * The "no station in range" branch is what makes one activation discoverable. A player who has taken Carpentry
   * and aims it at bare ground gets a workbench; aiming it at their workbench gets the recipe list. A skill that
   * only *uses* a station never places one, so aiming Weapon Repair at empty ground offers an empty list rather
   * than building a forge out of nothing.
   */
  private fun applyCrafting(
    world: World,
    casterId: EntityId,
    skillId: Long,
    targetPosition: Vec3L?,
    result: CraftingResult
  ) {
    val station = result.station

    if (result.placesStation && station != null) {
      val at = targetPosition ?: world.get(casterId, Position::class)?.toVec3L()

      if (at != null && playerStructureService.stationNear(world, at, station) == null) {
        val masterId = world.get(casterId, Master::class)?.masterId
        if (masterId == null) {
          LOG.debug { "Entity $casterId is not a master and cannot build a $station" }
          return
        }

        // Deferred for the reason the damage staging is: a system iterating the world cannot create an entity
        // inline, and inside a deferred block the structural changes apply immediately.
        world.defer { playerStructureService.place(world, station, masterId, at, yaw = 0f) }
        return
      }
    }

    craftingService.offerRecipes(world, casterId, skillId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
