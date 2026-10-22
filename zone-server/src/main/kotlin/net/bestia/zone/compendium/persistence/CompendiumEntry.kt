package net.bestia.zone.compendium.persistence

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
import net.bestia.zone.account.persistence.Master
import java.time.Instant

/**
 * One species a master knows from its Bestia Compendium.
 *
 * [speciesId] is not a foreign key: the mob importer deletes species that left the YAML, and a master's record
 * of having seen one must not block that.
 */
@Entity
@Table(
  name = "compendium_entry",
  uniqueConstraints = [UniqueConstraint(columnNames = ["master_id", "species_id"])]
)
class CompendiumEntry(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @Column(name = "species_id", nullable = false)
  val speciesId: Long,

  @Column(name = "first_seen_at", nullable = false)
  val firstSeenAt: Instant,

  @Column(name = "caught_at")
  var caughtAt: Instant? = null,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
