package net.bestia.zone.battle.ecs.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import kotlin.math.roundToInt
import org.springframework.stereotype.Component as SpringComponent

/**
 * What low [Stamina] does to a master or an owned bestia, per the docs' stamina section
 * (https://docs.bestia-game.net/docs/mechanics/combat/statusvalues/):
 *
 * - below 30 % walk speed falls linearly, to half at 0 %;
 * - below 10 % health and mana stop regenerating;
 * - below 5 % the body loses [StaminaConfig.drainHealthPercent] of its max health every
 *   [StaminaConfig.drainIntervalSeconds], and that can kill.
 *
 * Wild mobs are left alone: they carry a placeholder `Stamina(10, 10)` nothing spends.
 *
 * ### Why the speed moves in 5 % steps
 *
 * A changed [Exhaustion] costs a full `StatusValueRecalcSystem` pass (passives, gear, every status effect) and a
 * synced [net.bestia.zone.movement.ecs.Speed]. Following stamina point by point would run that on every regen
 * tick of every tired player; quantising the curve to 5 % of stamina caps it at six recalcs on the way down and
 * six on the way back up, and the player cannot tell the difference.
 *
 * ### Why the drain is not staged as `IncomingDamage`
 *
 * For the reason [net.bestia.zone.weather.EnvironmentalExposureSystem] gives: there is no attacker to credit, and exhaustion should not
 * start a combat timer - which would also stop the very regeneration that is meant to pull the entity out of it
 * once it has eaten. Like exposure it honours [DamageGate.isImmune] and marks death through [Dead.markOnce].
 */
@SpringComponent
class StaminaExhaustionSystem(
  private val config: StaminaConfig,
  private val messages: OutMessageProcessor,
) : System {
  override val phase = Phase.RECOVERY

  /**
   * After the regeneration it gates, so stamina regenerated this tick is weighed this tick, and the gate it sets
   * holds from the next regeneration on.
   */
  override val after = setOf(HpRegenSystem::class, ManaRegenSystem::class, StaminaRegenSystem::class)

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet =
    setOf(Stamina::class, Master::class, OwnedBestia::class, Account::class) + DamageGate.READS

  override val writes: ComponentClassSet =
    setOf(Exhaustion::class, IsStatusValueDirty::class, Health::class, Dead::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Stamina::class).each { id ->
      val isMaster = world.has(id, Master::class)
      if (!isMaster && !world.has(id, OwnedBestia::class)) return@each
      // A body lying dead keeps whatever it had; it neither slows further nor drains.
      if (world.has(id, Dead::class)) return@each

      val stamina = get<Stamina>()
      if (stamina.max <= 0) return@each

      val percent = stamina.current * 100 / stamina.max
      val band = StaminaBand.of(percent)
      val exhaustion = world.get(id, Exhaustion::class)

      track(world, id, exhaustion, band, speedPercentAt(percent), isMaster)

      // Asked of the band just measured, not the component's: a meal that lifted the entity out of the band
      // this second must stop the drain this second. A component added this tick is the band entered this
      // tick, so its clock starts on the next pass.
      if (exhaustion != null && band == StaminaBand.COLLAPSING) {
        drain(world, id, exhaustion, deltaTime)
      }
    }
  }

  /** Writes [Exhaustion] and asks for a recalc only when the band or the speed step moved. */
  private fun track(
    world: World,
    id: EntityId,
    exhaustion: Exhaustion?,
    band: StaminaBand,
    speedPercent: Int,
    isMaster: Boolean,
  ) {
    val oldBand = exhaustion?.band ?: StaminaBand.RESTED
    val oldSpeed = exhaustion?.speedPercent ?: FULL_SPEED_PERCENT
    if (band == oldBand && speedPercent == oldSpeed) return

    when {
      band == StaminaBand.RESTED -> world.remove(id, Exhaustion::class)
      exhaustion == null -> world.add(id, Exhaustion(band, speedPercent))
      else -> {
        if (band != StaminaBand.COLLAPSING) exhaustion.drainSeconds = 0f
        exhaustion.band = band
        exhaustion.speedPercent = speedPercent
      }
    }

    if (speedPercent != oldSpeed) {
      world.add(id, IsStatusValueDirty)
    }

    if (band > oldBand) {
      announce(world, id, band, isMaster)
    }
  }

  private fun drain(world: World, id: EntityId, exhaustion: Exhaustion, deltaTime: Float) {
    exhaustion.drainSeconds += deltaTime
    if (exhaustion.drainSeconds < config.drainIntervalSeconds) return
    exhaustion.drainSeconds -= config.drainIntervalSeconds

    if (DamageGate.isImmune(world, id)) return
    val health = world.get(id, Health::class) ?: return

    health.current -= drainAmount(health.max)

    if (health.current == 0 && Dead.markOnce(world, id)) {
      LOG.trace { "$id collapsed from exhaustion." }
    }
  }

  /** The 0.3 % of max health one drain takes, at least 1 so a small body is not spared by rounding. */
  fun drainAmount(maxHealth: Int): Int {
    return (maxHealth * config.drainHealthPercent / 100.0).roundToInt().coerceAtLeast(1)
  }

  /** The first time the player sees this band since last being above it: one system line, not one per second. */
  private fun announce(world: World, id: EntityId, band: StaminaBand, isMaster: Boolean) {
    val accountId = world.get(id, Account::class)?.accountId ?: return
    val code = when (band) {
      StaminaBand.RESTED -> return
      StaminaBand.TIRED -> if (isMaster) OpSuccess.STAMINA_TIRED else OpSuccess.BESTIA_STAMINA_TIRED
      StaminaBand.EXHAUSTED -> if (isMaster) OpSuccess.STAMINA_EXHAUSTED else OpSuccess.BESTIA_STAMINA_EXHAUSTED
      StaminaBand.COLLAPSING -> if (isMaster) OpSuccess.STAMINA_COLLAPSING else OpSuccess.BESTIA_STAMINA_COLLAPSING
    }

    messages.sendToPlayer(accountId, OperationSuccessSMSG(code))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val FULL_SPEED_PERCENT = 100

    /** Stamina is read in steps this wide, in percent; see the class KDoc. */
    private const val STEP_PERCENT = 5

    /** Walk speed left at 0 % stamina, in percent. */
    private const val EMPTY_SPEED_PERCENT = 50

    /**
     * The walk speed left at [staminaPercent]: full from [StaminaBand.TIRED]'s threshold up, falling linearly to
     * [EMPTY_SPEED_PERCENT] at 0, read in [STEP_PERCENT] steps (rounded down, so the first step lands as soon as
     * the player is tired).
     */
    fun speedPercentAt(staminaPercent: Int): Int {
      val threshold = StaminaBand.TIRED.belowPercent
      val stepped = (staminaPercent.coerceIn(0, threshold) / STEP_PERCENT) * STEP_PERCENT
      val lost = FULL_SPEED_PERCENT - EMPTY_SPEED_PERCENT

      return EMPTY_SPEED_PERCENT + (lost * stepped + threshold / 2) / threshold
    }
  }
}
