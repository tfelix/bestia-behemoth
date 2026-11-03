package net.bestia.zone.honor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.economy.PersonalPrice
import net.bestia.zone.economy.TraderStanding
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.honor.persistence.LocalHonor.Scope
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.townsfolk.TownsfolkRegard
import net.bestia.zone.townsfolk.ecs.Townsfolk
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.math.roundToInt

/**
 * The one place an act turns into honor: who saw it, which layers it reaches, and what a Master's honor adds up
 * to when somebody deals with them. Rules are in bestia-docs `server/honor.md`.
 */
@Service
class HonorService(
  catalog: HonorCatalog,
  private val localHonor: LocalHonorRegistry,
  private val sites: SettlementSiteIndex,
  private val clock: Clock = Clock.systemUTC(),
  private val outcry: WitnessOutcry? = null,
) : TraderStanding, TownsfolkRegard {

  private val rules = catalog.rules

  /** What happened to one act, for logs, the GM command and tests. */
  data class Recorded(
    val seen: Boolean,
    val globalChange: Int = 0,
    val grudges: List<Long> = emptyList(),
    val settlement: Int? = null,
  )

  fun componentFor(ledger: HonorLedger): Honor {
    return Honor(ledger, ledger.valueAt(clock.instant(), rules.global))
  }

  /**
   * Charges or rewards [actor], a Master entity, for [act] done at [at].
   *
   * Townsfolk who saw it hold a personal grudge; with nobody of the town in sight, an act inside a settlement
   * reaches the settlement directly. Player witnesses only make the global part count.
   */
  /** @param maxGain caps what a good deed earns globally, for a reward that depends on its victim */
  fun record(world: World, act: HonorAct, actor: EntityId, at: Vec3L, maxGain: Int? = null): Recorded {
    val honor = world.get(actor, Honor::class) ?: return Recorded(seen = false)
    val master = world.get(actor, Master::class) ?: return Recorded(seen = false)
    val masterId = master.masterId
    val masterName = master.name

    val warded = world.get(actor, StatusEffects::class)?.hasEffect(StatusEffectId.WARDED.id) == true
    val townsfolkInSight = townsfolkWitnesses(world, at)
    val townsfolk = townsfolkInSight.values.toList()
    val settlement = sites.siteCovering(at.x, at.y)?.index
    val seen = warded || townsfolk.isNotEmpty() || settlement != null || anyPlayerSees(world, actor, at)

    if (!seen) {
      LOG.debug { "$act by master $masterId at $at went unseen" }
      return Recorded(seen = false)
    }

    val amount = rules.amountOf(act)
    val global = scaled(amount.global, warded).let { if (maxGain != null) it.coerceAtMost(maxGain) else it }
    val local = scaled(amount.local, warded)

    val now = clock.instant()
    val before = honor.shownValue
    val ledger = honor.ledger.apply(global, now, rules.global)
    honor.update(ledger, ledger.valueAt(now, rules.global))

    val reachedSettlement = settlement.takeIf { townsfolk.isEmpty() }
    townsfolk.forEach { changeLocal(masterId, masterName, Scope.GRUDGE, it, local) }
    reachedSettlement?.let { changeLocal(masterId, masterName, Scope.SETTLEMENT, it.toLong(), local) }

    if (local < 0) {
      townsfolkInSight.keys.firstOrNull()?.let { outcry?.cryOut(world, it, act, masterName) }
    }

    LOG.debug { "$act by master $masterId: global $global, local $local on ${townsfolk.size} witness(es)" }

    return Recorded(
      seen = true,
      globalChange = honor.shownValue - before,
      grudges = townsfolk,
      settlement = reachedSettlement,
    )
  }

  /**
   * What [actor] is worth to the people of [settlement], and to [townsperson] in particular: the sum the
   * effect thresholds and shop prices are measured against.
   */
  fun combined(world: World, actor: EntityId, settlement: Int?, townsperson: Long? = null): Int {
    val honor = world.get(actor, Honor::class) ?: return 0
    val masterId = world.get(actor, Master::class)?.masterId ?: return 0
    val now = clock.instant()

    val global = honor.ledger.valueAt(now, rules.global)
    val town = settlement?.let { localValue(masterId, Scope.SETTLEMENT, it.toLong(), rules.settlement) } ?: 0
    val grudge = townsperson?.let { localValue(masterId, Scope.GRUDGE, it, rules.grudge) } ?: 0

    return global + town + grudge
  }

  override val reads: ComponentClassSet
    get() {
      return PRICE_READS
    }

  /** The formula is the one in pvp.md: `-5/3 * honor` percent below 0. */
  override fun priceFor(world: World, trader: EntityId, settlement: Int, merchant: EntityId): PersonalPrice? {
    val townsperson = world.get(merchant, Townsfolk::class)?.identity
    val honor = combined(world, trader, settlement, townsperson)
    val prices = rules.prices

    return when {
      honor < rules.hostileBelow -> null
      honor < 0 -> {
        val markup = -5.0 / 3.0 * honor / 100.0
        PersonalPrice(buyFactor = 1.0 + markup, sellFactor = 1.0 - markup)
      }
      else -> {
        val discount = ((honor - prices.discountAbove) * prices.discountPerHonor).coerceIn(0.0, prices.maxDiscount)
        PersonalPrice(buyFactor = 1.0 - discount, sellFactor = 1.0)
      }
    }
  }

  override fun isUnfriendly(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean {
    return combined(world, master, settlement, townsperson) < rules.unfriendlyBelow
  }

  override fun isFeared(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean {
    return combined(world, master, settlement, townsperson) < rules.hostileBelow
  }

  fun settlementValue(masterId: Long, settlement: Int): Int {
    return localValue(masterId, Scope.SETTLEMENT, settlement.toLong(), rules.settlement)
  }

  /** Heals what has healed since [honor] last changed. @return the value the owner now sees */
  fun refresh(honor: Honor): Int {
    honor.update(honor.ledger, honor.ledger.valueAt(clock.instant(), rules.global))

    return honor.shownValue
  }

  /** Replaces [actor]'s global honor outright, for GMs. */
  fun setGlobal(world: World, actor: EntityId, value: Int) {
    val honor = world.get(actor, Honor::class) ?: return
    val offset = value - rules.global.start
    val ledger = HonorLedger(
      earned = offset.coerceAtLeast(0),
      debt = (-offset).coerceAtLeast(0),
      lastLossAt = clock.instant().takeIf { offset < 0 },
    )

    honor.update(ledger, ledger.valueAt(clock.instant(), rules.global))
  }

  /** Changes what [settlement] thinks of a Master, as news reaches it rather than as somebody there saw it. */
  fun changeSettlement(masterId: Long, masterName: String, settlement: Int, amount: Int) {
    changeLocal(masterId, masterName, Scope.SETTLEMENT, settlement.toLong(), amount)
  }

  fun isRememberedForever(masterId: Long, settlement: Int): Boolean {
    return localHonor.ledgerOf(masterId, Scope.SETTLEMENT, settlement.toLong())?.permanent == true
  }

  private fun changeLocal(masterId: Long, masterName: String, scope: Scope, holder: Long, amount: Int) {
    if (amount == 0) {
      return
    }

    val layer = layerOf(scope)
    val current = localHonor.ledgerOf(masterId, scope, holder) ?: HonorLedger()
    val untold = if (scope == Scope.GRUDGE && amount < 0) -amount else 0

    localHonor.store(masterId, scope, holder, current.apply(amount, clock.instant(), layer), masterName, untold)
  }

  private fun localValue(masterId: Long, scope: Scope, holder: Long, layer: HonorLayer): Int {
    val ledger = localHonor.ledgerOf(masterId, scope, holder) ?: return layer.start

    return ledger.valueAt(clock.instant(), layer)
  }

  private fun layerOf(scope: Scope): HonorLayer {
    return when (scope) {
      Scope.SETTLEMENT -> rules.settlement
      Scope.GRUDGE -> rules.grudge
    }
  }

  private fun scaled(amount: Int, warded: Boolean): Int {
    if (!warded) {
      return amount
    }
    val factor = if (amount < 0) rules.lossFactorWarded else rules.gainFactorWarded

    return (amount * factor).roundToInt()
  }

  /** A killed townsperson tells nobody; a killed player still remembers who did it. */
  /** Each witness's entity to who they are. */
  private fun townsfolkWitnesses(world: World, at: Vec3L): Map<EntityId, Long> {
    val seenBy = LinkedHashMap<EntityId, Long>()
    world.each(Townsfolk::class) { id, townsfolk ->
      if (!world.has(id, Dead::class) && isInSight(world, id, at)) {
        seenBy[id] = townsfolk.identity
      }
    }

    return seenBy
  }

  private fun anyPlayerSees(world: World, actor: EntityId, at: Vec3L): Boolean {
    val actorAccount = world.get(actor, Account::class)?.accountId
    var seen = false
    world.each(Master::class) { id, _ ->
      if (!seen && world.get(id, Account::class)?.accountId != actorAccount && isInSight(world, id, at)) {
        seen = true
      }
    }

    return seen
  }

  /** Height is ignored, as the ward field ignores it. */
  private fun isInSight(world: World, id: EntityId, at: Vec3L): Boolean {
    val position = world.get(id, Position::class) ?: return false
    val dx = position.x - at.x
    val dy = position.y - at.y

    return dx * dx + dy * dy <= rules.witnessRange * rules.witnessRange
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** What [record] touches, for a system that calls it from inside the tick. */
    val READS: ComponentClassSet = setOf(
      Master::class, Account::class, StatusEffects::class, Townsfolk::class, Position::class, Dead::class,
    )
    val WRITES: ComponentClassSet = setOf(Honor::class)

    /** What [priceFor] reads. */
    val PRICE_READS: ComponentClassSet = setOf(Honor::class, Master::class, Townsfolk::class)
  }
}
