package net.bestia.zone.workexp

import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.config.WorkExpConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.spawn.ecs.KillExpLevy
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.min

/**
 * Pays EXP to the master behind whoever did the work, and only while that master plays: a bestia working for a
 * master who is away earns nothing.
 *
 * The guild levy applies as it does to kill EXP, so working funds the guild like fighting does.
 */
@Component
class WorkExpService(
  private val calculator: WorkExpCalculator,
  private val config: WorkExpConfig,
  private val masters: MasterEntityResolver,
  private val levies: List<KillExpLevy> = emptyList(),
  private val clock: Clock = Clock.systemUTC(),
) {

  /** What [award] touches, for a system that calls it to declare. */
  val reads: ComponentClassSet = setOf(Master::class, Level::class) + levies.flatMap { it.reads }
  val writes: ComponentClassSet = setOf(WorkExpLedger::class, GainExp::class)

  /** @return the EXP the master kept */
  fun award(world: World, workerId: EntityId, effortSeconds: Double): Int {
    val master = masters.masterEntityOf(world, workerId) ?: return 0
    val level = world.get(master, Level::class)?.level ?: return 0

    val effort = min(effortSeconds, calculator.effortCap(level))
    val ledger = world.get(master, WorkExpLedger::class) ?: world.add(master, WorkExpLedger())
    val paid = ledger.draw(clock.millis(), effort, config.bankSeconds)

    val earned = calculator.payout(level, paid)
    val kept = levies.fold(earned) { amount, levy -> levy.levy(world, master, amount) }
    if (kept > 0) {
      // Deferred like a kill's EXP, so two awards in one tick add up.
      world.defer { world.update(master, { GainExp() }) { it.value += kept } }
    }

    return kept
  }
}
