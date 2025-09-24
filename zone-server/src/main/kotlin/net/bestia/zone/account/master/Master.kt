package net.bestia.zone.account.master

import jakarta.persistence.*
import net.bestia.zone.account.Account
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.PlayerBestia
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.party.Party
import java.awt.Color

@Entity
@Table(
  name = "master",
  indexes = [
    Index(columnList = "name", unique = true)
  ]
)
class Master(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  val account: Account,

  @Column(length = 20)
  var name: String,

  @Column(name = "hairColor", columnDefinition = "CHAR(6)")
  var hairColor: Color,

  @Column(name = "skinColor", columnDefinition = "CHAR(6)")
  var skinColor: Color,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var hair: Hairstyle,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var face: Face,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var body: BodyType
) {

  var level: Int = 1
    set(value) {
      require(value > 0)

      field = value
    }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @ManyToOne
  @JoinColumn(name = "party_id", nullable = true)
  var party: Party? = null

  @OneToOne(mappedBy = "owner")
  var ownedParty: Party? = null

  @Embedded
  var position: Vec3L = Vec3L.ZERO

  @Embedded
  val inventory = MasterInventory().apply {
    master = this@Master
  }

  val bestias = MasterBestias()

  init {
    require(name.length <= MAX_NAME_LENGTH) { "Master name must be at most $MAX_NAME_LENGTH characters." }
  }

  fun addPlayerBestia(bestia: Bestia, policy: PlayerBestiaPolicy): PlayerBestia {
    return bestias.addBestia(this, bestia, policy)
  }

  override fun toString(): String {
    return "Master(id=$id, name=$name, pos=$position)"
  }

  companion object {
    private const val MAX_NAME_LENGTH = 20
  }
}
