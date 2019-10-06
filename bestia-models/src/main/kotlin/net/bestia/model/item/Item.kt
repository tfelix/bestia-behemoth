package net.bestia.model.item

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.AbstractEntity
import java.io.Serializable
import javax.persistence.*

/**
 * Items can be added to a players inventory. They can be used, traded, sold,
 * dropped etc.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "items")
data class Item(
    @Column(name = "item_db_name", unique = true, nullable = false)
    val itemDbName: String,

    @Column(nullable = false)
    val mesh: String,

    @JsonIgnore
    @Column(nullable = false)
    val price: Int = 0,

    /**
     * Weight of the item. The rule is: 100gr = 1 weight unit.
     */
    val weight: Int = 0,

    @Enumerated(EnumType.STRING)
    val type: ItemType,

    val level: Int = 1,

    /**
     * Maybe we should query a script for the dynamic range.
     */
    val usableDefaultRange: Int = 0,

    @OneToMany
    @JoinColumn(name="itemId")
    val recepies: MutableList<CraftRecipe> = mutableListOf()
) : AbstractEntity(), Serializable {
  override fun toString(): String {
    return "Item[dbName: $itemDbName, id: $id, type: $type]"
  }
}
