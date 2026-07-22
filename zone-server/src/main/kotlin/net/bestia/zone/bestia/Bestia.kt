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
   * Which [net.bestia.zone.item.equip.EquipmentSlot]s this species has at all, as a bitmask over
   * `EquipmentSlot.bit`. Authored per mob under `resources/mob/` and mirrored into the client's
   * static bestia DB by the `syncBestiaDb` Gradle task - it is static content, so the client reads
   * it from its own DB instead of receiving it online.
   */
  @Column(name = "equip_slot_mask", nullable = false)
  val equipSlotMask: Int = 0
) {

  init {
    requireValidIdentifier(identifier)
  }

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val lootTable: MutableSet<LootItem> = mutableSetOf()

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val skills: MutableSet<BestiaSkill> = mutableSetOf()
}
