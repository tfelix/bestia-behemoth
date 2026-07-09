package net.bestia.zone.bestia

import jakarta.persistence.*
import net.bestia.zone.battle.skill.Skill

@Entity
@Table(
  name = "bestia_skill",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["bestia_id", "skill_id"])
  ]
)
class BestiaSkill(

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bestia_id", nullable = false)
  val bestia: Bestia,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  val skill: Skill,

  @Column(name = "required_level", nullable = false)
  val requiredLevel: Int
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
