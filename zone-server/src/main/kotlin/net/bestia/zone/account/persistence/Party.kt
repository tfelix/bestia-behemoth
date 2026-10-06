package net.bestia.zone.account.persistence

import jakarta.persistence.*

@Entity
@Table(
  name = "party",
  indexes = [
    Index(columnList = "name", unique = true)
  ]
)
class Party(
  @OneToOne
  @JoinColumn(name = "master_id", nullable = false, unique = true)
  val owner: Master,

  var name: String
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
  val member: MutableSet<Master> = mutableSetOf()

  /**
   * Every master in the party, the owner included, each once. `Master.party` is set on the owner too, so a
   * party read back from the database lists its owner in [member] as well.
   */
  val everyone: Collection<Master>
    get() {
      return (member + owner).distinctBy { it.id }
    }

  val size: Int get() = everyone.size

  init {
    require(owner.party == null) {
      "Owner already has a party, remove party first"
    }
    require(name.isNotBlank()) { "Party name must not be blank" }
    owner.party = this
  }
}
