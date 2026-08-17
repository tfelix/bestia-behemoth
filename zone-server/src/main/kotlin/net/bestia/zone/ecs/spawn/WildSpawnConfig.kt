package net.bestia.zone.ecs.spawn

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How much of the generator's den field the server actually uses, and how hard.
 *
 * ### Why these knobs are here and not in `worldgen`
 *
 * The same split `ChunkStreamConfig` argues for. `worldgen`'s `SpawnerParams` are **birth settings**: they
 * decide where markers exist at all, they are folded into `pipelineVersion`, and changing one makes the boot
 * gate declare the stored world incompatible - which under the development `on-mismatch: REGENERATE` deletes
 * it. These are **runtime settings**: a den is rebuilt from the markers at every boot, so everything here
 * takes effect on a restart, moves no version and destroys nothing.
 *
 * That is what makes this the level designer's surface. "Level eighty dens should be sparser" is a balance
 * question that wants trying, reverting and trying again, and it must not cost a world each time.
 *
 * ### The one direction this cannot go
 *
 * **The server can only thin.** [Band.denShare] is capped at 1.0 because nothing here can invent a marker -
 * a den either came out of the generator or it does not exist. So `SpawnerParams.candidateSpacing` has to be
 * set for the *thickest* band anyone will ever want, which is the starter country, and every other band is
 * thinned down from it here. Wanting a band **thicker** than the generator produced is the one change that
 * is still a params edit and a world regeneration, and it is worth knowing that before reaching for
 * `den-share: 1.5` and finding it refused.
 */
@ConfigurationProperties(prefix = "wild-spawn")
data class WildSpawnConfig(
  val bands: List<Band> = DEFAULT_BANDS,

  /**
   * Species never placed by a den, whatever their bestia row says.
   *
   * The operational escape hatch, beside rather than instead of `Bestia.eventOnly`: that one is the content
   * statement ("a raid boss does not live in the wilderness"), this one is for a species that turns out to
   * be broken in the wild and has to stop spawning before anyone can edit and redeploy content.
   */
  val excludedSpecies: List<String> = emptyList(),

  /**
   * Metres a den wakes beyond its own spawn radius.
   *
   * Wider than a player's view so a pack is already standing where it belongs by the time anybody can see
   * it - a den that woke at the edge of vision would pop its creatures into existence in front of the
   * player. It is also the headroom that keeps `range + margin` inside
   * [SpawnerSystem.MAX_ACTIVATION_RANGE], which is what [maxRange] is derived from.
   */
  val activationMargin: Int = Spawner.DEFAULT_ACTIVATION_RANGE,

  /** Absolute ceiling on one den's pack after [Band.packMultiplier], so a runaway multiplier cannot bite. */
  val maxPack: Int = 40,

  /**
   * Degrees outside its window at which a species is drawn at [minTemperatureWeight] of its spawn weight.
   *
   * The slope of the soft constraint. Ten degrees is roughly the width of a biome's own spread, so a species
   * authored for temperate grassland is rare but not absent on the warm edge of one.
   */
  val temperatureFalloffCelsius: Double = 10.0,

  /**
   * Floor on the temperature penalty, as a share of the species' spawn weight.
   *
   * Set this to 0 to make temperature effectively a hard constraint. It is not zero by default for the
   * reason `Bestia.temperatureMinCelsius` gives: a mis-authored window would then produce a silently
   * unspawnable species.
   */
  val minTemperatureWeight: Double = 0.05
) {

  /**
   * One level band's share of the generator's dens.
   *
   * Keyed on the den's `LEVEL_MAX`, which is the same banding `Invariants.spawnerCensus` prints per seed -
   * so the generator's census and this config talk about the same four groups rather than two that nearly
   * agree.
   */
  data class Band(
    /** Inclusive top of this band. No default: an incomplete band should be a loud binding failure. */
    val maxLevel: Int,

    /** Share of this band's dens actually placed. Cannot exceed 1 - see the class KDoc. */
    val denShare: Double = 1.0,

    /** Scales the generator's pack size for this band. */
    val packMultiplier: Double = 1.0,

    /**
     * Scales how far a pack spreads from its den.
     *
     * The evenness knob, and the cheap one: it adds no dens at all, it changes whether a den reads as a
     * huddle or as a populated stretch of country. Bounded by [maxRange].
     */
    val radiusMultiplier: Double = 1.0
  ) {
    init {
      require(maxLevel >= 1) { "A band's max-level must be at least 1, was $maxLevel" }
      require(denShare in 0.0..1.0) {
        "den-share $denShare must be a share of the dens the generator produced; the server can only thin"
      }
      require(packMultiplier > 0.0) { "pack-multiplier must be positive, was $packMultiplier" }
      require(radiusMultiplier > 0.0) { "radius-multiplier must be positive, was $radiusMultiplier" }
    }
  }

  init {
    require(bands.isNotEmpty()) { "At least one band is needed, or no den could be placed at all" }
    require(bands.map { it.maxLevel } == bands.map { it.maxLevel }.sorted()) {
      "Bands must ascend by max-level, were ${bands.map { it.maxLevel }}"
    }
    // Spring *replaces* a defaulted list rather than merging into it, so a YAML block with three bands
    // silently drops the fourth. This is the only thing between that and an endgame that quietly inherits
    // whatever the last band happened to say.
    require(bands.last().maxLevel >= TOP_LEVEL) {
      "The last band must cover the top of the level range ($TOP_LEVEL), but stops at ${bands.last().maxLevel}"
    }
    require(activationMargin in 1 until SpawnerSystem.MAX_ACTIVATION_RANGE) {
      "activation-margin must be in 1..${SpawnerSystem.MAX_ACTIVATION_RANGE - 1}, was $activationMargin"
    }
    require(maxPack >= 1) { "max-pack must be at least 1, was $maxPack" }
    require(temperatureFalloffCelsius > 0.0) {
      "temperature-falloff-celsius must be positive, was $temperatureFalloffCelsius"
    }
    require(minTemperatureWeight in 0.0..1.0) {
      "min-temperature-weight must be a share, was $minTemperatureWeight"
    }
  }

  /** The band a den falls in, by its own [SpawnerChannels.LEVEL_MAX][maxLevel]-style ceiling. */
  fun bandFor(levelMax: Int): Band = bands.firstOrNull { levelMax <= it.maxLevel } ?: bands.last()

  /**
   * Largest `range` a den may be given, so its activation range still fits.
   *
   * `Spawner`'s init refuses a den whose activation range is below its spawn range, and `SpawnerCellIndex`
   * refuses an activation range past [SpawnerSystem.MAX_ACTIVATION_RANGE] - so a [Band.radiusMultiplier]
   * large enough to breach this would throw out of a constructor at boot and stop the server. It is clamped
   * against this instead, with one warning.
   */
  val maxRange: Int get() = SpawnerSystem.MAX_ACTIVATION_RANGE - activationMargin

  companion object {
    /** The cap `SpawnerParams.maxLevel` also uses; a band set that stops short of it is a config error. */
    const val TOP_LEVEL = 100

    /**
     * The shipped banding, matching `Invariants.spawnerCensus`' `1-8 / 9-40 / 41-79 / 80-100`.
     *
     * The endgame is thinned hard and deliberately: a level-ninety pack is a fight rather than scenery, and
     * the density that makes starter country feel alive would make the far mountains impassable.
     *
     * ### Where the multipliers come from
     *
     * Measured, not guessed - `WildSpawnDensityTest` prints the figures these were solved against. At 1.0
     * across the board the shipped world came out at 1.8 creatures on the average screen of country the
     * catalogue can stock, and only 0.83 dens reached that screen at all, which reads as knots of creatures
     * with empty ground between rather than as populated country.
     *
     * [Band.packMultiplier] fixes the count and [Band.radiusMultiplier] fixes the *spread*, and both are
     * needed: more creatures per den without a wider den is a bigger huddle in the same place. Together they
     * land at roughly two to three creatures on screen with about one den always reaching it.
     *
     * They live here rather than in `SpawnerParams` because they are balance rather than terrain - moving
     * them costs a restart, while moving the generator's own radius costs the world.
     */
    val DEFAULT_BANDS = listOf(
      Band(maxLevel = 9, packMultiplier = 1.4, radiusMultiplier = 1.25),
      Band(maxLevel = 40, packMultiplier = 1.4, radiusMultiplier = 1.25),
      Band(maxLevel = 79, denShare = 0.7, packMultiplier = 1.4, radiusMultiplier = 1.25),
      Band(maxLevel = TOP_LEVEL, denShare = 0.35, packMultiplier = 0.6)
    )
  }
}
