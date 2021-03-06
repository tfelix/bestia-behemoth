package net.bestia.model.guild

import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import java.io.Serializable
import java.time.Instant
import java.util.*
import javax.persistence.*

/**
 * Representation of a guild for the bestia game.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "guilds")
class Guild(
    @Column(nullable = false, unique = true, length = 40)
    val name: String,
    @OneToOne
    @JoinColumn(nullable = false, unique = true)
    private var leader: GuildMember
    // @Column(nullable = true)
    // var emblem: String? = null
) : AbstractEntity(), Serializable {

  var level = 1
    set(level) {
      require(!(level > MAX_GUILD_LEVEL || level < 0)) { "Guild level must be between 0 and $MAX_GUILD_LEVEL" }
      field = level
    }

  var exp = 0
    private set

  /**
   * Last time the leader of this guild was changed. If it is null then the
   * leader was never changed.
   */
  @Column(nullable = true)
  private val lastLeaderChange: Instant? = null

  /**
   * Date when the guild was created.
   */
  val created = Instant.now()

  @OneToMany(cascade = [CascadeType.ALL])
  @JoinColumn(name = "guild_id")
  private val members: MutableSet<GuildMember> = mutableSetOf()

  @OneToMany(cascade = [CascadeType.ALL])
  @JoinColumn(name = "guild_id")
  private val ranks: MutableSet<GuildRank> = mutableSetOf()

  init {
    require(!(name.isEmpty() || name.length > 40)) { "Guild name can not be null or empty or longer then 40 character." }
  }

  fun getMember(accountId: Long): GuildMember? {
    return members.firstOrNull { it.member.id == accountId }
  }

  fun allMembers(): Set<GuildMember> {
    return members
  }

  fun addGuildMember(account: Account): Boolean {
    if (members.size >= MAX_GUILD_LEVEL) {
      return false
    }

    members.add(GuildMember(this, account, GuildRank(GuildRank.DEFAULT_RANK_NAME)))

    return true
  }

  /**
   * @param member Removes this [GuildMember] from the guild.
   */
  fun removeMember(member: GuildMember) {
    this.members.remove(member)
  }

  fun allRanks(): Set<GuildRank> {
    return Collections.unmodifiableSet(ranks)
  }

  fun removeRank(rank: GuildRank) {
    this.ranks.remove(rank)
  }

  fun addRank(rank: GuildRank) {
    this.ranks.add(rank)
  }

  fun getPlayerBestiaIds(): Set<Long> {
    return members.map { it.member.id }.toSet()
  }

  fun addExp(exp: Int) {
    this.exp += exp
    // TODO Check level up
  }

  override fun toString(): String {
    return "Guild[id: $id, name: $name, lv: $level, #member: ${members.size}]"
  }

  companion object {
    @Transient
    val MAX_GUILD_LEVEL = 10
  }
}
