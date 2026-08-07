package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.skill.LearnedSkill

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

  /**
   * The owner's standing order for what this bestia does while they are not driving it.
   *
   * Belongs here rather than in the mob catalogue because it is per-creature player configuration, exactly like
   * [name] and [position] — the catalogue's `Bestia.aiProfile` decides what the species *can* do, this decides
   * what this individual is currently told to do. Replayed at spawn by
   * [net.bestia.zone.bestia.PlayerBestiaEntitySpawner].
   *
   * Defaulted, and every field of it defaulted, so rows written before the column existed read back as PATROL
   * rather than null. The schema is `ddl-auto: update` against a real database, so existing player bestias
   * survive the migration and must land somewhere sensible.
   */
  @Embedded
  var aiConfig: AiConfig = AiConfig()

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.BESTIA)

  @OneToMany(mappedBy = "playerBestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val learnedSkills: MutableSet<LearnedSkill> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}