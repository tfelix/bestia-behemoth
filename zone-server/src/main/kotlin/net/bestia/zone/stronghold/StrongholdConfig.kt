package net.bestia.zone.stronghold

import org.springframework.boot.context.properties.ConfigurationProperties

/** How many Empyrite Seeds are awake, and what keeps one from waking. See the Guild Strongholds page. */
@ConfigurationProperties(prefix = "stronghold")
data class StrongholdConfig(
  /** Awake at least this many seeds, however few guilds have Official Guild Approval. */
  val minAwakeSeeds: Int = 4,
  /** One more seed wakes for each this many approved guilds. */
  val guildsPerSeed: Int = 25,
  /** A seed this close to a player town or its sign site is skipped. Also the gap a new town keeps from a seed. */
  val settlementClearanceMetres: Double = 1_000.0,
  /** A master discovers a seed by walking within this of its centre: the seed's core. */
  val coreRadiusMetres: Double = 5.0,
  /** Official Guild Approval senses the awake, free seeds this far away. */
  val senseRangeMetres: Double = 30_000.0,
  /** It shows at most this many of them, nearest first. */
  val senseMarks: Int = 3,
  /** A sensed mark is off by up to this share of its distance. */
  val senseErrorShare: Double = 1.0 / 3.0,
  /** What planting an Empyrite costs the member who plants it. */
  val plantCoins: Long = 10_000,
  /** Work the Empyrite site needs, in total over every member working on it. */
  val siteBuildMinutes: Int = 30,
  /** A site nobody worked on for this long is given up. */
  val siteIdleHours: Long = 24,
  /** The Empyrite's health and defense before any Defense level. */
  val empyriteHp: Int = 50_000,
  val empyriteDefense: Int = 20,
  /** A Stronghold's grounds around its Empyrite, in metres. Guild skills that need the Stronghold work inside. */
  val groundsRadiusMetres: Double = 500.0,
  /** What each Defense level adds to the Empyrite. */
  val empyriteHpPerDefense: Int = 10_000,
  val empyriteDefensePerLevel: Int = 4,
  /** One step towards a track's next level costs this many coins times that level. */
  val stepCoinsPerLevel: Long = 20_000,
  /** With Permanent Development, the chance that a step counts twice. */
  val doubleStepChance: Double = 0.5,
  /** What hiring a steward costs the member who hires it. */
  val stewardCoins: Long = 200_000,
  /** Guild storage slots per level of the Guild Storage skill, and per Storage level of each Stronghold held. */
  val storageSlotsPerSkillLevel: Int = 100,
  val storageSlotsPerStorageLevel: Int = 50,
  /** Watcher posts: this many, plus one per Defense level, up to the most. */
  val watcherBasePosts: Int = 2,
  val watcherMaxPosts: Int = 12,
  /** A watcher's level: the base, plus this much per Defense level. */
  val watcherBaseLevel: Int = 50,
  val watcherLevelPerDefense: Int = 5,
  /** A fallen watcher comes back after this, or at once for the coins. */
  val watcherReturnMinutes: Long = 30,
  val watcherBringBackCoins: Long = 50_000,
  /** How far from its Empyrite a watcher roams and fights. */
  val watcherRoamMetres: Double = 120.0,
  /** A treasure drop every this many hours after planting, and at most this many wait; a new one replaces the oldest. */
  val treasuryDropHours: Long = 24,
  val treasuryWaitingDrops: Int = 3,
  /** A drop's coins: the base, plus this much per Economy level. */
  val treasuryCoinsBase: Long = 10_000,
  val treasuryCoinsPerEconomy: Long = 10_000,
  /** Ore from the nearest deposit kinds round the seed, never gold: this many pieces per Economy level of each. */
  val treasuryOreKinds: Int = 3,
  val treasuryOreRangeMetres: Double = 3_000.0,
  val treasuryOrePiecesPerEconomy: Int = 2,
  /** One crafting item per this many Economy levels, picked from those [treasuryCraftingItems] unlocks. */
  val treasuryEconomyPerCraftingItem: Int = 2,
  val treasuryCraftingItems: List<TreasuryItem> = emptyList(),
  /** After a capture, the new Empyrite cannot be harmed for this long. */
  val captureGraceMinutes: Long = 10,
  /** Weekly spans such as `SATURDAY 18:00-22:00` in which Strongholds can be attacked. Empty: always. */
  val attackWindows: List<String> = emptyList(),
  /** The time zone the attack windows are read in. */
  val attackWindowZone: String = "UTC",
) {
  init {
    require(minAwakeSeeds >= 0) { "stronghold.min-awake-seeds must not be negative, was $minAwakeSeeds" }
    require(guildsPerSeed > 0) { "stronghold.guilds-per-seed must be positive, was $guildsPerSeed" }
    require(settlementClearanceMetres >= 0.0) { "stronghold.settlement-clearance-metres must not be negative" }
    require(coreRadiusMetres > 0.0) { "stronghold.core-radius-metres must be positive, was $coreRadiusMetres" }
    require(senseRangeMetres > 0.0) { "stronghold.sense-range-metres must be positive, was $senseRangeMetres" }
    require(senseMarks > 0) { "stronghold.sense-marks must be positive, was $senseMarks" }
    require(senseErrorShare in 0.0..1.0) { "stronghold.sense-error-share must lie in 0..1, was $senseErrorShare" }
    require(plantCoins >= 0) { "stronghold.plant-coins must not be negative, was $plantCoins" }
    require(siteBuildMinutes > 0) { "stronghold.site-build-minutes must be positive, was $siteBuildMinutes" }
    require(siteIdleHours > 0) { "stronghold.site-idle-hours must be positive, was $siteIdleHours" }
    require(empyriteHp > 0) { "stronghold.empyrite-hp must be positive, was $empyriteHp" }
    require(empyriteDefense >= 0) { "stronghold.empyrite-defense must not be negative, was $empyriteDefense" }
    require(groundsRadiusMetres > 0.0) { "stronghold.grounds-radius-metres must be positive, was $groundsRadiusMetres" }
    require(captureGraceMinutes >= 0) { "stronghold.capture-grace-minutes must not be negative" }
    require(empyriteHpPerDefense >= 0 && empyriteDefensePerLevel >= 0) { "Defense must not weaken the Empyrite" }
    require(stepCoinsPerLevel >= 0) { "stronghold.step-coins-per-level must not be negative, was $stepCoinsPerLevel" }
    require(storageSlotsPerSkillLevel >= 0 && storageSlotsPerStorageLevel >= 0) { "Guild storage slots must not be negative" }
    require(watcherBasePosts in 0..watcherMaxPosts) { "stronghold.watcher-base-posts must lie in 0..watcher-max-posts" }
    require(watcherBaseLevel > 0 && watcherLevelPerDefense >= 0) { "Watcher levels must be positive" }
    require(watcherReturnMinutes >= 0 && watcherBringBackCoins >= 0) { "Watcher return time and price must not be negative" }
    require(watcherRoamMetres > 0.0) { "stronghold.watcher-roam-metres must be positive, was $watcherRoamMetres" }
    require(treasuryDropHours > 0 && treasuryWaitingDrops > 0) { "Treasure drops need a positive interval and room" }
    require(treasuryCoinsBase >= 0 && treasuryCoinsPerEconomy >= 0) { "Treasure coins must not be negative" }
    require(treasuryOreKinds >= 0 && treasuryOrePiecesPerEconomy >= 0) { "Treasure ore must not be negative" }
    require(treasuryEconomyPerCraftingItem > 0) { "stronghold.treasury-economy-per-crafting-item must be positive" }
    require(stewardCoins >= 0) { "stronghold.steward-coins must not be negative, was $stewardCoins" }
    require(doubleStepChance in 0.0..1.0) { "stronghold.double-step-chance must lie in 0..1, was $doubleStepChance" }
    // Parsed here as well as where they are used, so a typo stops the boot rather than the first siege.
    AttackWindows.of(this)
  }
}

/** A crafting item a treasure drop may bring once the Stronghold's Economy reaches [fromEconomy]. */
data class TreasuryItem(
  val item: String,
  val fromEconomy: Int,
)
