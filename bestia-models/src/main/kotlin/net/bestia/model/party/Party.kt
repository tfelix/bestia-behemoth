package net.bestia.model.party

import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import java.io.Serializable
import javax.persistence.*

/**
 * Holds all the players which are temporarily can form parties in order to
 * share experience for example or to chat.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "parties")
data class Party(
    @Column(nullable = false, unique = true, length = 25)
    var name: String
) : AbstractEntity(), Serializable {

  @OneToMany(cascade = [CascadeType.ALL])
  private val members: MutableSet<Account> = mutableSetOf()

  fun getMembers(): Set<Account> {
    return members.toSet()
  }

  /**
   * Adds a new member to the party.
   *
   * @param member
   * The new member to be added to the party.
   * @return TRUE if the member could be added. FALSE if it could not.
   */
  fun addMember(member: Account): Boolean {
    return if (members.size < MAX_PARTY_MEMBER && !members.contains(member)) {
      members.add(member)
      true
    } else {
      false
    }
  }

  fun removeMember(removeMember: Account) {
    members.removeIf { it.id == removeMember.id }
  }

  companion object {
    @Transient
    const val MAX_PARTY_MEMBER = 12
  }
}
