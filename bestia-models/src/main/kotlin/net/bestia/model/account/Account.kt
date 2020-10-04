package net.bestia.model.account

import net.bestia.model.AbstractEntity
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.party.Party
import java.io.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
@Table(name = "accounts")
data class Account(
    val username: String,

    val registerDate: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    val gender: Gender = Gender.NEUTRAL,

    @Enumerated(EnumType.STRING)
    val hairstyle: Hairstyle,

    var isActivated: Boolean = false
    ) : AbstractEntity(), Serializable {
  @Enumerated(EnumType.STRING)
  var accountType = AccountType.USER

  var additionalBestiaSlots: Int = 0

  var loginToken = ""

  @Column(nullable = true)
  var bannedUntil: ZonedDateTime? = null

  @ManyToOne
  var party: Party? = null

  @OneToOne
  var activeBestia: PlayerBestia? = null

  @OneToMany(cascade = [CascadeType.ALL], mappedBy = "owner")
  var playerBestias: MutableList<PlayerBestia> = mutableListOf()

  @OneToOne(cascade = [CascadeType.ALL], mappedBy = "master")
  var masterBestia: PlayerBestia? = null

  override fun toString(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate)
    return "Account[id: $id, username: ${username.take(5)}, registerDate: $dateStr]"
  }

  companion object {
    const val NUM_BESTIA_SLOTS = 4
  }
}

