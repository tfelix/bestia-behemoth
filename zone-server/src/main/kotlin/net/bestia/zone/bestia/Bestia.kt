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
  val level: Int,
  val experienceReward: Int,
  val health: Int,
  val mana: Int,
  /**
   * Identifier of the AI archetype (`resources/ai/<name>.yml`) that drives this mob, or null for a
   * mob without AI. H2 is in-memory and rebuilt on every boot, so no migration is needed.
   */
  val aiProfile: String? = null,

  /**
   * Identifier of the movement profile (`resources/movement/<name>.yml`) this species plans routes with, or
   * null for the default ground walker.
   *
   * Separate from [aiProfile] because the two answer different questions and species mix them freely: what a
   * creature *wants* is its AI archetype, and how it gets there - whether it swims, whether it avoids roads,
   * whether it fits over a footbridge - is this. A wolf and a bandit can share every goal and disagree about
   * rivers.
   */
  val movementProfile: String? = null,

  /**
   * Which [net.bestia.zone.item.equip.EquipmentSlot]s this species has at all, as a bitmask over
   * `EquipmentSlot.bit`. Authored per mob under `resources/mob/` and mirrored into the client's
   * static bestia DB by the `syncBestiaDb` Gradle task - it is static content, so the client reads
   * it from its own DB instead of receiving it online.
   */
  @Column(name = "equip_slot_mask", nullable = false)
  val equipSlotMask: Int = 0,

  /**
   * Biomes a wild spawner may place this species in, as a comma-separated list of
   * `net.bestia.worldgen.bio.Biome` **names**, or empty for "any biome the den's own rules allow".
   *
   * Names rather than ordinals because ordinals are the on-disk form of the `BIOME` raster and this is
   * authored content - a reordered enum must not silently re-home every mob in the game. H2 is rebuilt on
   * every boot, so a string column costs nothing here.
   */
  @Column(name = "habitat", nullable = false)
  val habitat: String = "",

  /** True when this species may only be placed by a den standing on corrupted ground. */
  @Column(name = "corrupted_only", nullable = false)
  val corruptedOnly: Boolean = false,

  /**
   * True when this species is a boss: placed only by a den whose `BOSS` channel is set, and one at a time.
   *
   * Separate from a level of 100, because a level-100 species that is *not* a boss is a legitimate thing to
   * author - a pack of them - and a den has to be able to tell the two apart before it decides how many to
   * keep alive.
   */
  @Column(name = "boss", nullable = false)
  val boss: Boolean = false,

  /**
   * Relative chance of being picked when several species fit a den, higher being likelier.
   *
   * A weight rather than a probability so a designer can add a species without restating every other one -
   * the same reason a loot table carries chances rather than shares.
   */
  @Column(name = "spawn_weight", nullable = false)
  val spawnWeight: Int = 100
) {

  init {
    requireValidIdentifier(identifier)
  }

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val lootTable: MutableSet<LootItem> = mutableSetOf()

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val skills: MutableSet<BestiaSkill> = mutableSetOf()
}
