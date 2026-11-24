package net.bestia.zone.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** A stored bestia's pools as they were when it went into storage. It comes out with them unchanged. */
@Embeddable
data class StoredVitals(
  @Column(name = "stored_health")
  val health: Int,
  @Column(name = "stored_mana")
  val mana: Int,
  @Column(name = "stored_stamina")
  val stamina: Int,
)
