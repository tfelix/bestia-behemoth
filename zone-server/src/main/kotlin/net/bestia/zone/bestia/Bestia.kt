package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.item.LootItem
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "bestia",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Bestia(
  val identifier: String,
  val level: Int,
  val experienceReward: Int,
  val health: Int,
  val mana: Int
) {

  init {
    requireValidIdentifier(identifier)
  }

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val lootTable: MutableSet<LootItem> = mutableSetOf()

  @OneToMany(mappedBy = "bestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val attacks: MutableSet<BestiaAttack> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
