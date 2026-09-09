package net.bestia.zone.ecs.spawn.ambient

import net.bestia.zone.ecs.spawn.WildSpawnConfig
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How thick the country between the dens is, and what that is allowed to cost.
 *
 * ### Why a layer beside the dens rather than more dens
 *
 * `WildSpawnConfig` can only *thin* what `SpawnerStage` produced, and the generator produced dens 350 m
 * apart holding packs in a box a few hundred metres wide - about 20 creatures per square kilometre, one per
 * two hundred metres of walking. Wanting one per thirty is forty times that, and the only lever the den
 * layer has for it is `candidateSpacing`, which is quadratic: it would mean millions of markers and a world
 * regeneration. So the baseline population is not markers at all. It is a function of the world seed and a
 * position, evaluated for the ground a player is standing on, and the dens stay exactly as they are on top
 * of it as the denser, higher-level, persistent concentrations.
 *
 * ### Everything here is a runtime setting
 *
 * The same split `WildSpawnConfig` argues. Nothing in this class is folded into `pipelineVersion`, nothing
 * is written down anywhere, and no ambient creature is persisted - so every knob takes effect on a restart
 * and none of them can cost a world. [spacingTiles] is the one worth pausing on: changing it renames every
 * lattice cell and so re-rolls the whole wilderness. That is harmless *here* and would not be for the dens,
 * where a persisted creature carries a `DenIdentity` naming a marker ordinal.
 */
@ConfigurationProperties(prefix = "ambient-spawn")
data class AmbientSpawnConfig(
  /** Off leaves exactly the den-only world this was added to. The knob to reach for first when in doubt. */
  val enabled: Boolean = true,

  /**
   * Tiles between neighbouring sites, before any thinning. One tile is one metre.
   *
   * 30 is one site per 900 m², about 1 100 per km², which puts four or so creatures in the ~60 m of ground
   * the camera actually shows. It is the headline knob and the one that costs: population scales with its
   * inverse square, so 20 is twice the creatures of 30 and 15 is four times.
   */
  val spacingTiles: Long = 30,

  /**
   * Share of sites removed where the country is at its most hostile.
   *
   * Applied against the same danger curve the dens use, so gentle country is thickest and the deserts and
   * ice thin out - and because that curve's largest term is distance to the nearest settlement, the fields
   * around a town come out fullest without anything here mentioning towns. At 0.6 a village's fields keep
   * about 94% of their sites and a volcanic field keeps 40%. Zero is uniform everywhere.
   */
  val dangerThinning: Double = 0.6,

  /**
   * Ceiling on an ambient creature's level.
   *
   * Corrupted ground is level 80 and up by construction, and eleven hundred level-ninety creatures per
   * square kilometre is not atmosphere, it is impassable terrain. The site is clamped rather than dropped:
   * remote harsh country should still hold wildlife, and what makes it dangerous is the dens standing in it
   * - which is `WildSpawnConfig`'s own argument that a high-level pack is a fight rather than scenery.
   */
  val maxLevel: Int = 40,

  /**
   * Tiles from a player within which sites are stocked.
   *
   * Wider than the interest range so a creature is already standing where it belongs before anybody can see
   * it, the same margin a den's activation range buys. It must clear the interest radius plus the largest
   * wander radius, or a creature that ambled towards the player was never spawned - which reads as flicker
   * rather than as a misconfiguration. `AmbientSpawnerBootRunner` checks it against the live
   * `InterestRange`, which is why the bound is not in this class's `init`.
   */
  val activationRadiusTiles: Long = 240,

  /**
   * Creatures created per pass, across every player.
   *
   * A global budget rather than a per-site one: a site holds a single creature, so without this a player
   * arriving in fresh country would create a hundred and forty entities in one tick. At the quarter-second
   * pass 12 is about 48 a second, filling a fresh view in roughly three - close to what the chunk stream
   * itself takes for a first load, and hidden inside [activationRadiusTiles] either way.
   */
  val spawnsPerPass: Int = 12,

  /**
   * Seconds a site keeps its creature after the last player leaves.
   *
   * Matches `SpawnerSystem.UNLOAD_DELAY_SECONDS` deliberately: long enough that stepping away and back does
   * not disturb anything, short enough that country crossed an hour ago is not still populated.
   */
  val unloadDelaySeconds: Float = 60f,

  /**
   * Seconds a site stays empty after its creature was killed.
   *
   * Without it a player standing on one spot has an endless supply, because the site is restocked on the
   * next pass. This is the one behaviour the den layer has and should not.
   */
  val respawnDelaySeconds: Float = 120f,

  /**
   * Tiles of clearance kept beyond a town's outermost building or wall.
   *
   * Measured from the built edge rather than the centre, because settlements are neither round nor all the
   * same size - a radius around the middle either leaves creatures in the streets of a city or empties the
   * country around a hamlet. Quantised up to `TownClearance.MASK_CELL_METRES`.
   */
  val townClearanceTiles: Long = 100,

  /**
   * Resolved sites held in memory.
   *
   * A site never changes, so this is a memo rather than a cache - there is nothing to invalidate. 200 000
   * covers roughly 180 km² of country already walked.
   */
  val siteCacheSize: Int = 200_000,

  /**
   * How much less often an ambient creature perceives, senses and thinks.
   *
   * 1 disables throttling without a code change. Only ever applied to creatures this layer created: a den's
   * pack, a boss, a `/spawn`ed mob and anything a player controls are never throttled, whatever this says.
   */
  val throttleFactor: Int = 4,

  /** AI profiles this layer refuses to throttle, for a species that needs full fidelity at any distance. */
  val neverThrottledProfiles: List<String> = emptyList()
) {

  init {
    require(spacingTiles >= 1) { "spacing-tiles must be at least 1, was $spacingTiles" }
    require(dangerThinning in 0.0..1.0) { "danger-thinning must be a share, was $dangerThinning" }
    require(maxLevel in 1..WildSpawnConfig.TOP_LEVEL) {
      "max-level must be in 1..${WildSpawnConfig.TOP_LEVEL}, was $maxLevel"
    }
    require(activationRadiusTiles >= 1) {
      "activation-radius-tiles must be positive, was $activationRadiusTiles"
    }
    require(spawnsPerPass >= 1) { "spawns-per-pass must be at least 1, was $spawnsPerPass" }
    require(unloadDelaySeconds >= 0f) { "unload-delay-seconds must not be negative" }
    require(respawnDelaySeconds >= 0f) { "respawn-delay-seconds must not be negative" }
    require(townClearanceTiles >= 0) { "town-clearance-tiles must not be negative" }
    require(siteCacheSize >= 1) { "site-cache-size must be at least 1, was $siteCacheSize" }
    require(throttleFactor >= 1) { "throttle-factor must be at least 1, was $throttleFactor" }
  }
}
