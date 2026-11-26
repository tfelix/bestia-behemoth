package net.bestia.zone.account.persistence

import jakarta.persistence.*
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyLootShare
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

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

  /** A varchar rather than a native ENUM: `ddl-auto: update` never widens an ENUM column when a mode is added. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "exp_share", nullable = false, length = 16)
  var expShare: PartyExpShare = PartyExpShare.EVEN_SHARE

  /** Defaulted in the schema too, so `ddl-auto: update` fills the parties that existed before the column. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @ColumnDefault("'PARTY_SHARE'")
  @Column(name = "loot_share", nullable = false, length = 16)
  var lootShare: PartyLootShare = PartyLootShare.PARTY_SHARE

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
