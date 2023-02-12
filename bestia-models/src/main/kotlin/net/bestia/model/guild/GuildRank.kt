package net.bestia.model.guild

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.bestia.model.AbstractEntity

/**
 * A rank in a guild allows the leader to give certain member different
 * rights.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "guild_ranks")
class GuildRank(
    @Column(nullable = false)
    var name: String
) : AbstractEntity() {

  var taxRate: Float = 0f
    set(value) {
      field = when {
        value < 0 -> 0f
        value > 50 -> 50f
        else -> value
      }
    }

  var canEditMember: Boolean = false
  var canEditRanks: Boolean = false

  override fun toString(): String {
    return "Rank[id: $id, name: $name]"
  }

  companion object {
    const val DEFAULT_RANK_NAME: String = "Rookie"
  }
}
