package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.battle.skill.LearnedSkill

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

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.BESTIA)

  @OneToMany(mappedBy = "playerBestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val learnedSkills: MutableSet<LearnedSkill> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}