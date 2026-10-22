package net.bestia.zone.guild.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * One thing that happened in a guild. Holds names rather than masters: the log outlives members who leave and
 * masters who are deleted.
 */
@Entity
@Table(
  name = "guild_log_entry",
  indexes = [Index(columnList = "guild_id, id")]
)
class GuildLogEntry(
  @ManyToOne(optional = false)
  @JoinColumn(name = "guild_id", nullable = false)
  val guild: Guild,

  /** A varchar rather than a native ENUM: `ddl-auto: update` never widens an ENUM column when a kind is added. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  val kind: GuildLogKind,

  @Column(name = "actor_name", nullable = false, length = 32)
  val actorName: String,

  @Column(name = "target_name", nullable = false, length = 32)
  val targetName: String = "",

  @Column(nullable = false, length = 64)
  val detail: String = "",
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = false)
  val at: Instant = Instant.now()
}
