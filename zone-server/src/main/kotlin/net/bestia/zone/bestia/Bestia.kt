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
  val aiProfile: String? = null
) {

  init {
    requireValidIdentifier(identifier)
  }

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val lootTable: MutableSet<LootItem> = mutableSetOf()

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val skills: MutableSet<BestiaSkill> = mutableSetOf()
}
