package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.InventoryItem
import net.bestia.zone.battle.attack.LearnedAttack

@Entity
@Table(
  name = "player_bestia",
  indexes = [
    Index(columnList = "name", unique = true)
  ]
)
class PlayerBestia(
  @ManyToOne
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @ManyToOne
  @JoinColumn(name = "bestia_id", nullable = false)
  val bestia: Bestia,

  var name: String? = null
) {
  var level: Int = 1
    set(value) {
      require(value > 0)
      field = value
    }

  @Embedded
  var position: Vec3L = Vec3L.ZERO

  @OneToMany(mappedBy = "playerBestia")
  val inventory: MutableSet<InventoryItem> = mutableSetOf()

  @OneToMany(mappedBy = "playerBestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val learnedAttacks: MutableSet<LearnedAttack> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}