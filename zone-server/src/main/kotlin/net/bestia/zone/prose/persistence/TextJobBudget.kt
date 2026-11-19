package net.bestia.zone.prose.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * How long the text job has worked on one language of this world. Kept across restarts, so a world gets its
 * time once and a restart does not start the clock again.
 */
@Entity
@Table(name = "text_job_budget")
class TextJobBudget(
  @Id
  @Column(length = 8)
  var locale: String = "",

  @Column(name = "spent_millis", nullable = false)
  var spentMillis: Long = 0,
)
