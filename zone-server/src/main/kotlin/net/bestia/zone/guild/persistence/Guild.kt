package net.bestia.zone.guild.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.skill.SkillId
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

  /** Guild skill levels by `skills.yml` identifier; a skill never invested in has no entry. */
  @ElementCollection
  @CollectionTable(name = "guild_skill_level", joinColumns = [JoinColumn(name = "guild_id")])
  @MapKeyColumn(name = "skill", length = 64)
  @Column(name = "level", nullable = false)
  val skillLevels: MutableMap<String, Int> = mutableMapOf()

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now()

  @OneToMany(mappedBy = "guild", cascade = [CascadeType.ALL], orphanRemoval = true)
  val ranks: MutableList<GuildRank> = mutableListOf()

  @OneToMany(mappedBy = "guild", cascade = [CascadeType.ALL], orphanRemoval = true)
  val members: MutableList<GuildMember> = mutableListOf()

  /** Seats more members; see `GuildLevels.maxMembers`. */
  val extensionLevel: Int
    get() = skillLevel(SkillId.GUILD_EXTENSION.name)

  val spentSkillPoints: Int
    get() = skillLevels.values.sum()

  fun skillLevel(identifier: String): Int {
    return skillLevels[identifier] ?: 0
  }

  fun memberOf(masterId: Long): GuildMember? = members.firstOrNull { it.master.id == masterId }

  fun rank(rankId: Long): GuildRank? = ranks.firstOrNull { it.id == rankId }

  /** The rank a newcomer starts on: the one furthest from the leader's. */
  val lowestRank: GuildRank
    get() = ranks.maxBy { it.position }
}
