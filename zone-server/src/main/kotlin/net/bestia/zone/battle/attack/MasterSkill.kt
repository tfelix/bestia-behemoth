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

@Entity
@Table(
  name = "master_skill",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["master_id", "attack_id"])
  ]
)
class MasterSkill(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_id", nullable = false)
  val attack: Attack,
) {

  @Column(name = "required_level", nullable = false)
  var skillLevel: Int = 1

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}