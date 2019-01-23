package net.bestia.model.item

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import java.io.Serializable
import javax.persistence.*

typealias PlayerItemId = Long

@Entity
@Table(name = "player_items", uniqueConstraints = [
  UniqueConstraint(columnNames = arrayOf("ITEM_ID", "ACCOUNT_ID"))
])
data class PlayerItem(
    /**
     * Sets the owner of the item. Note that there can not be one account owning
     * the same number of items. Just add the amount instead. Setting the
     * account to null wont work. Delete the relationship instead.
     *
     * @param account
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    @JsonIgnore
    val account: Account,
    /**
     * Sets the item. Since there is some special treatment to equipment type
     * items the amount of equipment can only be 1 (equipment can not be stacked
     * inside the inventory, since additional information is attached to each
     * player item equipment).
     *
     * @param item
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ITEM_ID", nullable = false)
    @JsonProperty("i")
    val item: Item
) : AbstractEntity(), Serializable {

  /**
   * Sets the amount of the owned item. Amount must be bigger then 0.
   * Otherwise delete the relationship. Amounts of equipment type items can
   * not be different then 1.
   *
   * @param amount
   * The amount to set. Must be bigger then 0.
   */
  @JsonProperty("a")
  var amount: Int = 0
    set(amount) {
      if (amount <= 0) {
        throw IllegalArgumentException("Amount must be bigger then 0.")
      }

      if (this.item.type === ItemType.EQUIP && amount != 1) {
        throw IllegalArgumentException(
            "Amount of equipments must be equal to 1")
      }

      field = amount
    }

  /**
   * @param item
   * Item to add to the player inventory.
   * @param account
   * The player account to add the item.
   * @param amount
   * The amount of the item to be added.
   */
  constructor(item: Item, account: Account, amount: Int) : this(account, item) {
    this.amount = amount
  }
}
