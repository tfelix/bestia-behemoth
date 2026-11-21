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
import org.hibernate.annotations.ColumnDefault

/**
 * A step in a guild's hierarchy. [position] 0 is the leader's rank, which keeps every permission; a higher
 * position is a lower rank. Positions are not renumbered when a rank goes, so they may have gaps.
 *
 * The permissions are columns rather than an enum set: an `@Enumerated` column would become a native ENUM
 * that `ddl-auto: update` never widens.
 */
@Entity
@Table(
  name = "guild_rank",
  uniqueConstraints = [UniqueConstraint(columnNames = ["guild_id", "position"])]
)
class GuildRank(
  @ManyToOne(optional = false)
  @JoinColumn(name = "guild_id", nullable = false)
  val guild: Guild,

  @Column(nullable = false)
  val position: Int,

  @Column(nullable = false, length = 32)
  var name: String,

  /** The share of a member's kill EXP that goes to the guild, in percent. */
  @Column(name = "tax_percent", nullable = false)
  var taxPercent: Int,

  @Column(name = "can_invite", nullable = false)
  var canInvite: Boolean = false,

  @Column(name = "can_kick", nullable = false)
  var canKick: Boolean = false,

  @Column(name = "can_manage_ranks", nullable = false)
  var canManageRanks: Boolean = false,

  /** May cast the guild's active skills. */
  @Column(name = "can_use_guild_skills", nullable = false)
  var canUseGuildSkills: Boolean = false,

  @Column(name = "can_edit_motd", nullable = false)
  var canEditMotd: Boolean = false,

  /** May put items into the guild storage. Every rank may, until the leader says otherwise. */
  @Column(name = "can_deposit", nullable = false)
  @ColumnDefault("true")
  var canDeposit: Boolean = true,

  /** May take items out of the guild storage. Every take is in the guild log. */
  @Column(name = "can_take", nullable = false)
  @ColumnDefault("false")
  var canTake: Boolean = false,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  val isLeaderRank: Boolean
    get() = position == LEADER_POSITION

  companion object {
    const val LEADER_POSITION = 0
  }
}
