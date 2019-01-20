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
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Item(
    @Column(name = "item_db_name", unique = true, nullable = false)
    @JsonProperty("idbn")
    val itemDbName: String,

    @Column(nullable = false)
    @JsonProperty("img")
    val image: String,

    @JsonIgnore
    @Column(nullable = false)
    val price: Int = 0,

    /**
     * Weight of the item. The rule is: 100gr = 1 weight unit.
     */
    @Column(nullable = false)
    @JsonProperty("w")
    val weight: Int = 0,

    @Enumerated(EnumType.STRING)
    @JsonProperty("t")
    val type: ItemType,

    var usedSlot: EquipmentSlot? = null,

    @JsonProperty("i")
    val indicator: String? = null,

    @JsonProperty("lv")
    val level: Int = 1,

    /**
     * Maybe we should query a script for the dynamic range.
     */
    val usableDefaultRange: Int = 0
) : AbstractEntity(), Serializable {
  override fun toString(): String {
    return "Item[dbName: $itemDbName, id: $id, type: $type]"
  }
}
