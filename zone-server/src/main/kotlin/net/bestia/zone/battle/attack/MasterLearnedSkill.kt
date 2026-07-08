package net.bestia.zone.battle.attack

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.account.master.Master

/**
 * How many points a specific [master] has currently invested into a specific skill tree node
 * ([skill]). The static tree shape (which skills exist, their max level, their prerequisites)
 * is defined by [MasterSkillTreeNode] / [MasterSkillPrerequisite]; this table only tracks the
 * per-master, per-skill invested level.
 */
@Entity
@Table(
  name = "master_learned_skill",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["master_id", "skill_id"])
  ]
)
class MasterLearnedSkill(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  val skill: Skill
) {

  @Column(name = "level", nullable = false)
  var level: Int = 1

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
