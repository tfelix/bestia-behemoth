package net.bestia.model.domain

import java.io.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "accounts")
data class Account(
    @Column(length = 64, unique = true, nullable = false)
    var email: String,

    @Embedded
    var password: Password,

    val username: String,

    var registerDate: Instant,

    @Enumerated(EnumType.STRING)
    var gender: Gender
) : Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(unique = true, nullable = false)
  var id: Long = 0

  var activeBestiaEntityId: Long = 0

  var loginToken = ""

  var isActivated = false

  var remarks = ""

  var additionalBestiaSlots: Int = 0

  @Column(nullable = false)
  var language = "en"

  @Column(nullable = false)
  var bannedUntilDate: Instant? = null

  @Enumerated(EnumType.STRING)
  var userLevel = UserLevel.USER

  var hairstyle = Hairstyle.female_01

  override fun toString(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate)
    return "Account[id: $id, registerDate: $dateStr]"
  }

  companion object {
    enum class UserLevel {
      USER, GM, SUPER_GM, ADMIN
    }
  }
}
