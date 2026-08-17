package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.item.loot.LootItem
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "bestia",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Bestia(
  @Id
  var id: Long = 0,

  val identifier: String,
  var level: Int,
  var experienceReward: Int,
  var health: Int,
  var mana: Int,
  /**
   * Identifier of the AI archetype (`resources/ai/<name>.yml`) that drives this mob, or null for a
   * mob without AI.
   */
  var aiProfile: String? = null,

  /**
   * Identifier of the movement profile (`resources/movement/<name>.yml`) this species plans routes with, or
   * null for the default ground walker.
   *
   * Separate from [aiProfile] because the two answer different questions and species mix them freely: what a
   * creature *wants* is its AI archetype, and how it gets there - whether it swims, whether it avoids roads,
   * whether it fits over a footbridge - is this. A wolf and a bandit can share every goal and disagree about
   * rivers.
   */
  var movementProfile: String? = null,

  /**
   * Which [net.bestia.zone.item.equip.EquipmentSlot]s this species has at all, as a bitmask over
   * `EquipmentSlot.bit`. Authored per mob under `resources/mob/` and mirrored into the client's
   * static bestia DB by the `syncBestiaDb` Gradle task - it is static content, so the client reads
   * it from its own DB instead of receiving it online.
   */
  @Column(name = "equip_slot_mask", nullable = false)
  var equipSlotMask: Int = 0,

  /**
   * Biomes a wild spawner may place this species in, as a comma-separated list of
   * `net.bestia.worldgen.bio.Biome` **names**, or empty for "any biome the den's own rules allow".
   *
   * Names rather than ordinals because ordinals are the on-disk form of the `BIOME` raster and this is
   * authored content - a reordered enum must not silently re-home every mob in the game.
   *
   * **The one constraint the wild spawner never relaxes.** When no species fits a den's level band, the
   * selection falls back to one that does not - but never to one whose habitat excludes the den's biome,
   * because a blob in a volcanic field is not a compromise, it is nonsense. See `WildSpawnerService`.
   */
  @Column(name = "habitat", nullable = false)
  var habitat: String = "",

  /** True when this species may only be placed by a den standing on corrupted ground. */
  @Column(name = "corrupted_only", nullable = false)
  var corruptedOnly: Boolean = false,

  /**
   * True when this species is a boss: placed only by a den whose `BOSS` channel is set, and one at a time.
   *
   * Separate from a level of 100, because a level-100 species that is *not* a boss is a legitimate thing to
   * author - a pack of them - and a den has to be able to tell the two apart before it decides how many to
   * keep alive.
   */
  @Column(name = "boss", nullable = false)
  var boss: Boolean = false,

  /**
   * Relative chance of being picked when several species fit a den, higher being likelier.
   *
   * A weight rather than a probability so a designer can add a species without restating every other one -
   * the same reason a loot table carries chances rather than shares.
   */
  @Column(name = "spawn_weight", nullable = false)
  var spawnWeight: Int = 100,

  /**
   * True when nothing may place this species automatically: no wild den, ever. Scripts only.
   *
   * Distinct from the three ways a species can *happen* to be unplaceable today, and that is why it is its
   * own column rather than a trick with the others. An empty [habitat] means "any biome", not "nowhere". A
   * [spawnWeight] of zero is coerced back to one by the draw, because a weight is relative and a zero would
   * otherwise make the arithmetic ambiguous. And a level outside every den's band no longer excludes
   * anything at all, now that the level constraint is the one the fallback relaxes.
   *
   * A property of the species rather than an entry in a config list because "this is a raid boss, it does
   * not live in the wilderness" is a content fact, not an operational decision. `wild-spawn.excluded-species`
   * is the operational one, and exists beside this rather than instead of it.
   */
  @Column(name = "event_only", nullable = false)
  var eventOnly: Boolean = false,

  /**
   * Mean annual air temperature in degrees Celsius this species prefers, or null for no preference. Both
   * bounds are set or neither - the importer refuses a one-sided window.
   *
   * A **preference, not a requirement**: a den outside the window can still draw this species, at a reduced
   * weight, and `wild-spawn.min-temperature-weight` is how sharply. Soft on purpose. [habitat] already
   * carries the categorical answer - nothing tropical lives on an ice sheet because no den is there - so
   * this is the finer axis *inside* a biome, where `GRASSLAND` spans cold steppe and warm savanna. Making it
   * hard would mean a mis-authored twenty-degree window silently produced an unspawnable species, which is
   * exactly the failure the habitat validation exists to prevent.
   */
  @Column(name = "temperature_min_celsius")
  var temperatureMinCelsius: Double? = null,

  @Column(name = "temperature_max_celsius")
  var temperatureMaxCelsius: Double? = null
) {

  init {
    requireValidIdentifier(identifier)
  }

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val lootTable: MutableSet<LootItem> = mutableSetOf()

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val skills: MutableSet<BestiaSkill> = mutableSetOf()
}
