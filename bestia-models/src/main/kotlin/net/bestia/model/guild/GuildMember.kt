package net.bestia.model.guild

import net.bestia.model.AbstractEntity
import net.bestia.model.bestia.PlayerBestia
import javax.persistence.*

/**
 * Describes a member of a guild. This needs to be an extra entity because we
 * save additional information about each guild member like tax values, given
 * exp and some guild specific stuff.
 *
 * @author Thomas Felix
 */
@Entity
data class GuildMember(
    @ManyToOne(fetch = FetchType.LAZY)
    var guild: Guild,

    @ManyToOne(fetch = FetchType.LAZY)
    val member: PlayerBestia,

    @ManyToOne(fetch = FetchType.LAZY)
    val rank: GuildRank
) : AbstractEntity() {
  var expEarned: Int = 0
    private set

  /**
   * @param expEarned Adds the amount to the earned exp.
   */
  fun addExpEarned(expEarned: Int) {
    this.expEarned += expEarned
  }
}





