package net.bestia.model.account

import net.bestia.model.AbstractEntity
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

  override fun toString(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate)
    return "Account[id: $id, registerDate: $dateStr]"
  }
}

