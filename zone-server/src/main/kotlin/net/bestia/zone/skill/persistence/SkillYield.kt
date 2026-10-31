package net.bestia.zone.skill.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** One item a gathering skill may turn up, with its [chance] in basis points (10 000 is certain). */
@Embeddable
data class SkillYield(
  @Column(name = "item_id", nullable = false)
  val itemId: Long,

  @Column(nullable = false)
  val chance: Int,
) {
  constructor() : this(0L, 0)
}
