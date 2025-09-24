package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.battle.attack.Attack

@Entity
@Table(
  name = "bestia_attack",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["bestia_id", "attack_id"])
  ]
)
class BestiaAttack(

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bestia_id", nullable = false)
  val bestia: Bestia,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_id", nullable = false)
  val attack: Attack,

  @Column(name = "required_level", nullable = false)
  val requiredLevel: Int
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}