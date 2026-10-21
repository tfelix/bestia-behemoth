package net.bestia.zone.guild.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.account.persistence.Master
import java.time.Instant

/**
 * A guild, founded by spending a Founder's Seal. Its [leader] stays its leader: there is no transfer yet, and
 * the leader can neither be kicked nor leave, only disband it.
 *
 * Nothing on [Master] points here: who is in which guild is [GuildMember]'s, so a guild change never writes a
 * master row the persister is writing too.
 */
@Entity
@Table(
  name = "guild",
  indexes = [Index(columnList = "name", unique = true)]
)
class Guild(
  @Column(nullable = false, length = 32)
  var name: String,

  @OneToOne(optional = false)
  @JoinColumn(name = "leader_master_id", nullable = false, unique = true)
  val leader: Master,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = false)
  var level: Int = 1

  /** Towards the next level; see `GuildLevels`. */
  @Column(nullable = false)
  var exp: Long = 0

  /** Guild Extension, the one guild skill: each level seats more members. */
  @Column(name = "extension_level", nullable = false)
  var extensionLevel: Int = 0

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now()

  @OneToMany(mappedBy = "guild", cascade = [CascadeType.ALL], orphanRemoval = true)
  val ranks: MutableList<GuildRank> = mutableListOf()

  @OneToMany(mappedBy = "guild", cascade = [CascadeType.ALL], orphanRemoval = true)
  val members: MutableList<GuildMember> = mutableListOf()

  fun memberOf(masterId: Long): GuildMember? = members.firstOrNull { it.master.id == masterId }

  fun rank(rankId: Long): GuildRank? = ranks.firstOrNull { it.id == rankId }

  /** The rank a newcomer starts on: the one furthest from the leader's. */
  val lowestRank: GuildRank
    get() = ranks.maxBy { it.position }
}
