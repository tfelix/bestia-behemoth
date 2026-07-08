package net.bestia.zone.battle.attack

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.ManyToOne
import jakarta.persistence.JoinColumn
import jakarta.persistence.FetchType
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.bestia.PlayerBestia

/**
 * A custom skill an individual captured bestia has learned, on top of whatever its species'
 * fixed level-up table grants. It also contains the skills of the bestia master (see
 * [net.bestia.zone.battle.attack.MasterLearnedSkill]).
 */
@Entity
@Table(
  name = "learned_skill",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["player_bestia_id", "skill_id"])
  ]
)
class LearnedSkill(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  val skill: Skill,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "player_bestia_id", nullable = false)
  val playerBestia: PlayerBestia
) {

  @Column(name = "level", nullable = false)
  var level: Int = 1

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
