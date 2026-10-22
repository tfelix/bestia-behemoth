package net.bestia.zone.guild.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A master kicked out of a guild, as Ragnarok Online keeps them: no invitation reaches it until the entry is
 * lifted. The master by id and name, not as a reference: the entry outlives a deleted master.
 */
@Entity
@Table(
  name = "guild_expulsion",
  uniqueConstraints = [UniqueConstraint(columnNames = ["guild_id", "master_id"])]
)
class GuildExpulsion(
  @ManyToOne(optional = false)
  @JoinColumn(name = "guild_id", nullable = false)
  val guild: Guild,

  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Column(name = "master_name", nullable = false, length = 32)
  val masterName: String,

  @Column(nullable = false, length = 60)
  var reason: String,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = false)
  var at: Instant = Instant.now()
}
