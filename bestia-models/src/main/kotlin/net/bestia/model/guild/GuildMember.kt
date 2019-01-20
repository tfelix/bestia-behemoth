package net.bestia.model.guild

import net.bestia.model.AbstractEntity
import net.bestia.model.domain.PlayerBestia
import javax.persistence.*

/**
 * Describes a member of a guild. This needs to be an extra entity because we
 * save additional information about each guild member like tax values, given
 * exp and some guild specific stuff.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "guild_member", uniqueConstraints = [
  UniqueConstraint(columnNames = arrayOf("GUILD_ID", "PLAYER_BESTIA_ID"))
])
data class GuildMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GUILD_ID", nullable = false)
    var guild: Guild,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLAYER_BESTIA_ID", nullable = false)
    val member: PlayerBestia,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RANK_ID", nullable = true)
    val rank: GuildRank
) : AbstractEntity() {
  /**
   * @param expEarned
   * The expEarned to set
   */
  var expEarned: Int = 0

  /**
   * @param expEarned Adds the amount to the earned exp.
   */
  fun addExpEarned(expEarned: Int) {
    this.expEarned += expEarned
  }
}





