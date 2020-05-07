package net.bestia.model.account

import net.bestia.model.AbstractEntity
import java.io.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "accounts")
data class Account(
    val username: String,
    var registerDate: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    val gender: Gender,
    var isActivated: Boolean = false,
    var language: String = "en"
) : AbstractEntity(), Serializable {
  @Enumerated(EnumType.STRING)
  var userLevel = AccountType.USER
  var additionalBestiaSlots: Int = 0
  var loginToken = ""

  @Column(nullable = true)
  var bannedUntil: Instant? = null

  override fun toString(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate)
    return "Account[id: $id, registerDate: $dateStr]"
  }
}

