package net.bestia.zone.doomsday.voidtear

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.DoomShape
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.DoomThreat
import net.bestia.zone.doomsday.persistence.StoredVoidTear
import net.bestia.zone.doomsday.persistence.StoredVoidTearRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaGround
import net.bestia.zone.world.mana.WoundFootprint
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.RiftConfig
import net.bestia.zone.worldevent.rift.RiftField
import net.bestia.zone.worldevent.rift.RiftLifecycle
import net.bestia.zone.worldevent.rift.Rifts
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Void Tear: a rift at the strongest Wound that never closes on its own. Its stage follows the doom clock, so its
 * hordes and its anchor grow with the doom. It pours mana around itself and seeds rift knots ever further out.
 *
 * Players hold it back by sealing the rifts within its reach, and by sealing the Tear itself, which holds the clock
 * back and keeps the Tear closed for a few days. A knot the Tear swallows brings the end closer.
 */
@Component
class VoidTearThreat(
  private val config: VoidTearConfig,
  private val riftConfig: RiftConfig,
  private val rifts: Rifts,
  private val tears: StoredVoidTearRepository,
  private val field: ManaField,
  private val ground: ManaGround,
  private val concentration: ManaConcentrationService,
  private val provinces: ProvinceService,
  private val eventGround: EventGround,
  private val worldService: WorldService,
  private val bestiaClock: BestiaClock,
  private val clock: Clock = Clock.systemUTC(),
  private val random: Random = Random.Default,
) : DoomThreat {

  override val kind = DoomKind.VOID_TEAR

  override val shape = DoomShape.ACTOR

  private val lifecycle by lazy { RiftLifecycle(riftConfig, RiftField(field, ground), 1.0 / bestiaClock.speedFactor) }

  private val wounds: List<Vec2d> by lazy {
    worldService.generated.world.features.all()
      .filter { it.kind == FeatureKind.WOUND }
      .filterIsInstance<PointMarker>()
      .map { it.position }
      .filterNot { provinces.isSanctuaryAt(it.x, it.y) }
  }

  /**
   * The rifts within its reach as of the last step, to notice which were sealed and which it swallowed. Held in memory:
   * a restart forgets the rifts that end before they are seen again.
   */
  private val watched = HashMap<Long, Vec2d>()

  @Volatile
  private var last: DoomFate? = null

  private var footprint: WoundFootprint? = null

  override fun fits(): Boolean {
    return wounds.isNotEmpty()
  }

  /** The Wound where the mana is thickest. */
  override fun origin(random: Random): Vec2d {
    return wounds.maxBy { concentration.densityAt(Vec3L(it.x.toLong(), it.y.toLong(), 0)) }
  }

  /** The open rifts within its reach feed it. */
  override fun pressure(): Double {
    val fate = last ?: return 0.0
    val tearId = tears.findById(StoredVoidTear.ONLY).orElse(null)?.riftId
    val open = rifts.active().count { it.phase == Phase.OPEN && it.id != tearId && isWithinReach(it, fate) }
    return (open * PRESSURE_PER_OPEN_RIFT).coerceAtMost(1.0)
  }

  /** Each rift sealed within its reach in the last week holds it back. */
  override fun containment(): Double {
    val row = tears.findById(StoredVoidTear.ONLY).orElse(null) ?: return 0.0
    val since = today() - config.containmentWindowDays
    return (sealDaysOf(row).count { it >= since } * config.containmentPerSeal).coerceAtMost(1.0)
  }

  override fun step(fate: DoomFate, realSeconds: Double): Double {
    last = fate
    val row = tears.findById(StoredVoidTear.ONLY).orElseGet { StoredVoidTear() }

    var pushed = 0.0
    if (fate.stage >= DoomStage.ONSET) {
      pushed += tend(row, fate)
      pour(fate, realSeconds)
      seed(fate, realSeconds)
    }
    pushed += settleWatched(row, fate)
    tears.save(row)
    return pushed
  }

  /** Keeps the Tear open and in step with the clock, or notices that it was sealed. */
  private fun tend(row: StoredVoidTear, fate: DoomFate): Double {
    val riftId = row.riftId
    if (riftId != null) {
      return when (rifts.phaseOf(riftId)) {
        Phase.OPEN, Phase.KNOT -> {
          keepPace(riftId, fate)
          0.0
        }
        Phase.SEALED -> {
          row.riftId = null
          row.reformsAtDay = today() + config.reformDays
          LOG.info { "The Void Tear was sealed. It tears open again in ${config.reformDays} days" }
          config.sealPush
        }
        // Another rift swallowed it: that one is the Tear now.
        else -> {
          row.riftId = tearNear(fate)?.id
          0.0
        }
      }
    }

    val reformsAt = row.reformsAtDay
    if (reformsAt == null || today() >= reformsAt) {
      row.riftId = open(fate).id
      row.reformsAtDay = null
    }
    return 0.0
  }

  private fun open(fate: DoomFate): StoredRift {
    val now = now()
    val province = provinces.provinceAt(fate.originX, fate.originY)?.index ?: NO_PROVINCE
    val rift = rifts.formKnot(fate.originX, fate.originY, province, now, now)
    synchronized(field) {
      rift.phase = Phase.OPEN
      rift.stage = 1
      rift.opensAtSecond = now
      rift.collapsesAtSecond = now + NEVER_BESTIA_SECONDS
    }
    rifts.saveAll(listOf(rift))
    LOG.warn { "The Void Tear opened at (${fate.originX.toInt()}, ${fate.originY.toInt()})" }
    return rift
  }

  /** An open rift's stage follows its age, so the Tear's age is set to match the clock's stage. */
  private fun keepPace(riftId: Long, fate: DoomFate) {
    val rift = rifts.active().firstOrNull { it.id == riftId } ?: return
    val stageSeconds = lifecycle.openBestiaSeconds() / RiftLifecycle.STAGES
    val now = now()
    synchronized(field) {
      rift.opensAtSecond = now - (riftStageOf(fate.progress) - 1) * stageSeconds
      rift.collapsesAtSecond = now + NEVER_BESTIA_SECONDS
    }
    rifts.saveAll(listOf(rift))
  }

  private fun pour(fate: DoomFate, realSeconds: Double) {
    val motesPerDay = lerp(config.motesPerDayAtOnset, config.motesPerDayAtEnd, fate.progress)
    val footprint = footprint ?: WoundFootprint(fate.originX, fate.originY, config.pourReachMetres, field)
    this.footprint = footprint
    footprint.pour(field, motesPerDay * realSeconds / SECONDS_PER_DAY)
  }

  /** Seeds knots within its reach, each where it holds: the Tear lends the ground the mana a knot needs. */
  private fun seed(fate: DoomFate, realSeconds: Double) {
    val expected = lerp(config.knotsPerDayAtOnset, config.knotsPerDayAtEnd, fate.progress) * realSeconds / SECONDS_PER_DAY
    val count = floor(expected).toInt() + if (random.nextDouble() < expected - floor(expected)) 1 else 0
    repeat(count) { seedOne(fate) }
  }

  private fun seedOne(fate: DoomFate) {
    val angle = random.nextDouble(0.0, 2 * Math.PI)
    val distance = random.nextDouble(config.pourReachMetres, seedReach(fate.progress))
    val x = fate.originX + cos(angle) * distance
    val y = fate.originY + sin(angle) * distance

    val province = provinces.provinceAt(x, y) ?: return
    val taken = rifts.active().any { it.provinceIndex == province.index }
    if (provinces.isSanctuary(province) || taken || eventGround.grounded(x, y) == null) {
      return
    }

    val at = Vec3L(x.toLong(), y.toLong(), 0)
    if (concentration.densityAt(at) < SEEDED_DENSITY) {
      concentration.setDensity(at, SEEDED_DENSITY, riftConfig.knotRadiusMetres + SEED_MARGIN_METRES)
    }
    val now = now()
    val knotDays = random.nextDouble(riftConfig.knotMinBestiaDays, riftConfig.knotMaxBestiaDays)
    val knot = rifts.formKnot(x, y, province.index, now, now + (knotDays * BESTIA_SECONDS_PER_DAY).toLong())
    watched[knot.id] = Vec2d(x, y)
  }

  /** Counts the rifts within its reach that ended since the last step: sealed ones hold it back, swallowed ones feed it. */
  private fun settleWatched(row: StoredVoidTear, fate: DoomFate): Double {
    rifts.active()
      .filter { it.id != row.riftId && isWithinReach(it, fate) }
      .forEach { watched[it.id] = Vec2d(it.x, it.y) }

    var pushed = 0.0
    val ended = watched.keys.filter { rifts.phaseOf(it)?.isActive != true }
    for (id in ended) {
      val at = watched.remove(id) ?: continue
      when (rifts.phaseOf(id)) {
        Phase.SEALED -> row.recentSeals = recordSeal(row)
        Phase.MERGED -> if (hypot(at.x - fate.originX, at.y - fate.originY) <= SWALLOW_METRES) {
          pushed += config.swallowPush
        }
        else -> {}
      }
    }
    return pushed
  }

  private fun recordSeal(row: StoredVoidTear): String {
    val since = today() - config.containmentWindowDays
    return (sealDaysOf(row).filter { it >= since } + today()).joinToString(",")
  }

  private fun sealDaysOf(row: StoredVoidTear): List<Double> {
    return row.recentSeals.split(',').mapNotNull { it.toDoubleOrNull() }
  }

  private fun tearNear(fate: DoomFate): StoredRift? {
    return rifts.active().firstOrNull { hypot(it.x - fate.originX, it.y - fate.originY) <= SWALLOW_METRES }
  }

  private fun isWithinReach(rift: StoredRift, fate: DoomFate): Boolean {
    return hypot(rift.x - fate.originX, rift.y - fate.originY) <= seedReach(fate.progress)
  }

  private fun seedReach(progress: Double): Double {
    return lerp(config.seedReachAtOnsetMetres, config.seedReachAtEndMetres, progress)
  }

  private fun now(): Long {
    return bestiaClock.now().absoluteSecond
  }

  private fun today(): Double {
    return clock.millis() / 1000.0 / SECONDS_PER_DAY
  }

  private fun lerp(from: Double, to: Double, progress: Double): Double {
    return from + (to - from) * progress.coerceIn(0.0, 1.0)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val SECONDS_PER_DAY = 86_400.0
    const val BESTIA_SECONDS_PER_DAY = 86_400.0
    const val PRESSURE_PER_OPEN_RIFT = 0.15

    /** Enough for a knot to hold: rifts dissolve below 0.6. */
    const val SEEDED_DENSITY = 0.7
    const val SEED_MARGIN_METRES = 100.0

    /** A rift that ended this close to the Tear was swallowed by it. */
    const val SWALLOW_METRES = 2_000.0

    /** Its collapse is put this far ahead on every step, so it never comes. */
    const val NEVER_BESTIA_SECONDS = 10L * 365 * 86_400

    const val NO_PROVINCE = -1

    /** The rift stage, 1 to 5, for a doom clock at [progress]: 1 at onset, 5 in the Cataclysm. */
    fun riftStageOf(progress: Double): Int {
      val share = (progress - DoomStage.ONSET.from) / (DoomStage.CATACLYSM.from - DoomStage.ONSET.from)
      return (1 + floor(share * (RiftLifecycle.STAGES - 1)).toInt()).coerceIn(1, RiftLifecycle.STAGES)
    }
  }
}
