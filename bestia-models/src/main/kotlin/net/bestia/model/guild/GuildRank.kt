package net.bestia.model.guild

import net.bestia.model.AbstractEntity
import javax.persistence.*

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

  private var canEditMember: Boolean = false
  private var canEditRanks: Boolean = false

  override fun toString(): String {
    return "Rank[id: $id, name: $name]"
  }
}
