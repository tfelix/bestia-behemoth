package net.bestia.zone.guild.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.account.persistence.Master
import java.time.Instant

/** A master's place in a guild. A master is in at most one guild, which the unique [master] column holds to. */
@Entity
@Table(name = "guild_member")
class GuildMember(
  @ManyToOne(optional = false)
  @JoinColumn(name = "guild_id", nullable = false)
  val guild: Guild,

  @OneToOne(optional = false)
  @JoinColumn(name = "master_id", nullable = false, unique = true)
  val master: Master,

  @ManyToOne(optional = false)
  @JoinColumn(name = "rank_id", nullable = false)
  var rank: GuildRank,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(name = "joined_at", nullable = false)
  val joinedAt: Instant = Instant.now()

  /** Tax this member has paid into the guild, for the roster. */
  @Column(name = "exp_contributed", nullable = false)
  var expContributed: Long = 0

  val isLeader: Boolean
    get() = guild.leader.id == master.id
}
