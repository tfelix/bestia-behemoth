package net.bestia.zone.battle.attack

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
 * Internally it is called attack, but it also contains the skills of the bestia master.
 */
@Entity
@Table(
  name = "learned_attack",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["player_bestia_id", "attack_id"])
  ]
)
class LearnedAttack(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_id", nullable = false)
  val attack: Attack,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "player_bestia_id", nullable = false)
  val playerBestia: PlayerBestia
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}