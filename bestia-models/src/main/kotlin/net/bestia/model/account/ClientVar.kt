package net.bestia.model.account

import net.bestia.model.AbstractEntity
import java.io.Serializable
import java.nio.charset.Charset
import java.util.Objects

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.Table

/**
 * Clients can save data to the server in order to persist values.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "clientvars", indexes = [
  Index(columnList = "cvar_key", name = "key_idx")
])
data class ClientVar(
    @OneToOne
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    val account: Account,

    @Column(name = "cvar_key")
    val key: String
) : AbstractEntity(), Serializable {

  /**
   * Data containing this shortcut.
   */
  private var data: ByteArray? = null

  val dataLength: Int
    get() = data?.size ?: 0

  fun getDataAsString(): String {
    return data?.let { String(it, UTF_8) } ?: ""
  }

  fun setData(data: String) {
    this.data = data.toByteArray(UTF_8)
  }

  override fun toString(): String {
    return String.format("Cvar[accId: %d, key: %s, data: %s]", account.id, key, data)
  }

  companion object {
    private val UTF_8 = Charset.forName("UTF-8")
  }
}
