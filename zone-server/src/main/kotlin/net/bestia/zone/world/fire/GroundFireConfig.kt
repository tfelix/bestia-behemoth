package net.bestia.zone.world.fire

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for spreading grass fire.
 *
 * Runtime rather than world settings, in `ChunkStreamConfig`'s sense: none of these decides what the terrain
 * is, so all of them may change on a restart without consequence. A fire in flight is not persisted either -
 * `AreaEffectSpawner` makes the same argument for a spell effect that outlived a restart being a bug.
 *
 * ### Every cap here logs when it binds
 *
 * This subsystem's failure mode is a fire that quietly stops spreading, which from the outside is
 * indistinguishable from a fire that went out on its own. So no cap truncates silently; each one names what it
 * dropped, at a level matching how much it should worry somebody.
 *
 * @property stepSeconds how often a fire's cells advance. 0.6 s at the defaults, which with [burnSteps] gives
 *   a cell about five seconds alight - long enough to see, short enough that a front is a line rather than a
 *   filled disc.
 * @property burnSteps how many steps a cell burns before it is scorched and out. Also what makes a fire with
 *   nowhere left to go die on its own rather than needing a rule for it.
 * @property damageIntervalSeconds how often the effect covering a burning cluster ticks. Independent of
 *   [stepSeconds] on purpose: how fast fire *spreads* and how fast it *hurts* are different questions, and
 *   tying them would mean retuning damage every time the front speed moved.
 * @property baseIgnition probability an adjacent cell catches per step, before fuel, wind and rain. At 0.35
 *   with fuel 0.8 and dryness 0.7 an outward neighbour is around 0.2, which is a front of roughly a metre a
 *   second - inside the 17 to 170 m/min real grass fires run at.
 * @property windGain how much a full-strength downwind bearing multiplies ignition. Three means a gale-driven
 *   front runs about four times as fast downwind as across it, which is what makes wind worth reading.
 * @property windReferenceSpeed metres per second at which [windGain] is fully applied. 18 sits inside the
 *   model's 3-to-25 range, so an ordinary day gets part of the effect and a storm saturates it.
 * @property rainGain how much falling rain suppresses ignition. One means intensity 1.0 stops spread dead,
 *   which is the behaviour worth having: standing in a downpour should be a way to survive a grass fire.
 * @property downpourIntensity rain at or above which every burning cell is put out at once, rather than
 *   merely failing to spread. Snow is covered twice over - low dryness and an unburnable cap - which is
 *   deliberate belt and braces rather than an oversight.
 * @property maxConcurrentFires ignitions past this are refused outright with a warning naming the position.
 *   Refused rather than merged, because a merge would silently move somebody's fire.
 * @property maxBurningCellsPerFire a fire at this stops *igniting* but keeps burning down what it holds, so
 *   it dies out rather than freezing mid-spread. 4096 is a disc about 72 m across.
 * @property maxBurningCellsTotal the same ceiling across every fire at once, so a hundred small fires cannot
 *   do what one large one is stopped from doing.
 * @property maxIgnitionsPerStep per fire, so one step of a very long front cannot stall a tick. Deferred
 *   rather than dropped: the cells it did not reach are still adjacent to fire next step.
 * @property cellStepBudgetPerTick total cells advanced per tick across every fire. A fire that misses its
 *   turn keeps its accumulator, so nothing is lost, only deferred - the property `AreaEffectSystem`'s `while`
 *   loop has for the same reason.
 * @property maxLifetimeSeconds a hard stop, and reaching it is a **bug** rather than gameplay: a fire should
 *   run out of fuel or be rained on long before five minutes. It warns for that reason.
 */
@ConfigurationProperties(prefix = "ground-fire")
data class GroundFireConfig(
  val stepSeconds: Float = 0.6f,
  val burnSteps: Int = 8,
  val damageIntervalSeconds: Float = 1.0f,

  val baseIgnition: Double = 0.35,
  val windGain: Double = 3.0,
  val windReferenceSpeed: Double = 18.0,
  val rainGain: Double = 1.0,
  val downpourIntensity: Double = 0.5,

  val maxConcurrentFires: Int = 24,
  val maxBurningCellsPerFire: Int = 4096,
  val maxBurningCellsTotal: Int = 16_384,
  val maxIgnitionsPerStep: Int = 256,
  val cellStepBudgetPerTick: Int = 8_192,
  val maxLifetimeSeconds: Float = 300f,

  /** Damage a burning cluster deals per tick. Shaped rather than balanced, as `prop-kinds.yml` says of max-hp. */
  val damagePerTick: Int = 6,
) {
  init {
    require(stepSeconds > 0f) { "stepSeconds must be positive" }
    require(burnSteps >= 1) { "burnSteps must be at least 1" }
    require(damageIntervalSeconds > 0f) { "damageIntervalSeconds must be positive" }
    require(baseIgnition in 0.0..1.0) { "baseIgnition must be a probability" }
    require(windGain >= 0.0) { "windGain must not be negative" }
    require(windReferenceSpeed > 0.0) { "windReferenceSpeed must be positive" }
    require(rainGain >= 0.0) { "rainGain must not be negative" }
    require(downpourIntensity in 0.0..1.0) { "downpourIntensity must be a share" }
    require(maxConcurrentFires >= 1) { "maxConcurrentFires must be at least 1" }
    require(maxBurningCellsPerFire >= 1) { "maxBurningCellsPerFire must be at least 1" }
    require(maxBurningCellsTotal >= maxBurningCellsPerFire) {
      "maxBurningCellsTotal $maxBurningCellsTotal is below the per-fire cap $maxBurningCellsPerFire"
    }
    require(maxIgnitionsPerStep >= 1) { "maxIgnitionsPerStep must be at least 1" }
    require(cellStepBudgetPerTick >= 1) { "cellStepBudgetPerTick must be at least 1" }
    require(maxLifetimeSeconds > 0f) { "maxLifetimeSeconds must be positive" }
    require(damagePerTick >= 1) { "damagePerTick must be at least 1" }
  }
}
