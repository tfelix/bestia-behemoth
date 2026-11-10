package net.bestia.zone.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * An attack a bred bestia got from its father: one its species does not learn, or learns later. It learns it at
 * [learnLevel], like the rest of its learnset.
 */
@Entity
@Table(name = "player_bestia_inherited_attack")
class InheritedAttack(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "player_bestia_id", nullable = false)
  val playerBestia: PlayerBestia,

  @Column(name = "skill_id", nullable = false)
  val skillId: Long,

  @Column(name = "learn_level", nullable = false)
  val learnLevel: Int,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
