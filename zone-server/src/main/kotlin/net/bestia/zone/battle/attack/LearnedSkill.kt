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
import net.bestia.zone.account.master.Master
import net.bestia.zone.bestia.PlayerBestia
import org.hibernate.annotations.Check

/**
 * A single learned skill, owned by exactly one of [playerBestia] or [master]: either a custom
 * skill an individual captured bestia has learned on top of its species' fixed level-up table, or
 * a bestia master's invested level in a node of the master skill tree (see
 * [net.bestia.zone.battle.attack.MasterSkillTreeRegistry]).
 */
@Entity
@Table(
  name = "learned_skill",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["player_bestia_id", "skill_id"]),
    UniqueConstraint(columnNames = ["master_id", "skill_id"])
  ]
)
@Check(constraints = "(player_bestia_id IS NULL) != (master_id IS NULL)")
class LearnedSkill(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  val skill: Skill,

  @Column(name = "level", nullable = false)
  var level: Int = 1,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "player_bestia_id", nullable = true)
  val playerBestia: PlayerBestia? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "master_id", nullable = true)
  val master: Master? = null
) {

  init {
    require((playerBestia == null) != (master == null)) {
      "LearnedSkill must have exactly one owner: either a playerBestia or a master"
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
