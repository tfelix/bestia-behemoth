package net.bestia.zone.bestia.persistence

import jakarta.persistence.*
import net.bestia.zone.skill.persistence.Skill

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
  val requiredLevel: Int,

  /** Whether a wild one fights with it. The docs pick the newest attack of each archetype, more for a higher tier. */
  @Column(nullable = false)
  val active: Boolean = false,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
