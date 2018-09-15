package net.bestia.model.domain

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.Transient

import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "accounts")
class Account : Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(unique = true, nullable = false)
  var id: Long = 0
    private set

  @Column(length = 64, unique = true, nullable = false)
  var email = ""

  @Embedded
  var password: Password? = null

  var additionalBestiaSlots = 0
  var gold = 0
    set(gold) {
      if (gold < 0) {
        throw IllegalArgumentException("Gold value can not be negative.")
      }
      field = gold
    }
  var loginToken = ""

  @Temporal(TemporalType.DATE)
  private var registerDate: Date? = null

  @Temporal(TemporalType.DATE)
  private var lastLogin: Date? = null

  var isActivated = false

  var remarks = ""

  @Column(nullable = false)
  private var language = "en"

  @Temporal(TemporalType.DATE)
  private var bannedUntilDate: Date? = null

  @Enumerated(EnumType.STRING)
  var gender: Gender? = null

  @Enumerated(EnumType.STRING)
  var userLevel = UserLevel.USER

  var hairstyle = Hairstyle.female_01

  @ManyToOne(cascade = arrayOf(CascadeType.ALL))
  private val party: Party? = null

  // @OneToMany(mappedBy="account")
  // private List<GuildMember> guild;

  @OneToMany(cascade = arrayOf(CascadeType.ALL), mappedBy = "account", fetch = FetchType.LAZY)
  private val items = HashSet<PlayerItem>(0)

  @OneToOne(cascade = arrayOf(CascadeType.ALL), mappedBy = "master")
  @get:JsonIgnore
  var master: PlayerBestia? = null
    set(masterBestia) {
      if (masterBestia == null) {
        throw IllegalArgumentException("MasterBestia can not be null.")
      }
      field = masterBestia
    }

  @OneToMany(cascade = arrayOf(CascadeType.ALL), mappedBy = "owner")
  private val bestias = ArrayList<PlayerBestia>()

  /**
   * Returns the username of the account, the name of the bestia master.
   *
   * @return
   */
  val name: String
    get() = if (this.master == null) {
      ""
    } else this.master!!.name

  enum class UserLevel {
    USER, GM, SUPER_GM, ADMIN
  }

  constructor() {
    this.email = ""
    this.password = Password()
    setRegisterDate(Date())
    setLastLogin(Date())
    setBannedUntilDate(Date())
  }

  constructor(email: String?, password: String?) {
    if (email == null || email.isEmpty()) {
      throw IllegalArgumentException("Email can not be null or empty.")
    }
    if (password == null || password.isEmpty()) {
      throw IllegalArgumentException("Password can not be null or empty.")
    }
    val m = EMAIL_PATTERN.matcher(email)
    if (!m.matches()) {
      throw IllegalArgumentException("Email is not valid: $email")
    }

    this.email = email
    this.password = Password(password)
    setRegisterDate(Date())
    setLastLogin(Date())
    setBannedUntilDate(Date())
  }

  fun setId(id: Long?) {
    this.id = id!!
  }

  fun getBestias(): List<PlayerBestia> {
    return java.util.Collections.unmodifiableList(bestias)
  }

  fun getBannedUntilDate(): Date {
    return bannedUntilDate!!.clone() as Date
  }

  fun setBannedUntilDate(bannedUntilDate: Date) {
    this.bannedUntilDate = bannedUntilDate.clone() as Date
  }

  fun getRegisterDate(): Date {
    return registerDate!!.clone() as Date
  }

  fun setRegisterDate(registerDate: Date) {
    this.registerDate = registerDate.clone() as Date
  }

  fun getLastLogin(): Date {
    return lastLogin!!.clone() as Date
  }

  fun setLastLogin(lastLogin: Date) {
    this.lastLogin = lastLogin.clone() as Date
  }

  override fun hashCode(): Int {
    return email.hashCode()
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj) {
      return true
    }
    if (obj == null || obj !is Account) {
      return false
    }

    val other = obj as Account?
    return email == other!!.email
  }

  override fun toString(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate)
    return String.format("Account[id: %d, email: %s, registerDate: %s]", id, email, dateStr)
  }

  fun getLanguage(): Locale {
    return Locale(language)
  }

  fun setLanguage(locale: Locale) {
    this.language = locale.language
  }

  companion object {

    @Transient
    private val EMAIL_PATTERN = Pattern
            .compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$")
    @Transient
    private const val serialVersionUID = 1L
  }
}
