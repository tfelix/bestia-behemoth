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

  val size: Int get() = member.size + 1 // + 1 is the owner himself.

  init {
    require(owner.party == null) {
      "Owner already has a party, remove party first"
    }
    require(name.isNotBlank()) { "Party name must not be blank" }
    owner.party = this
  }
}
