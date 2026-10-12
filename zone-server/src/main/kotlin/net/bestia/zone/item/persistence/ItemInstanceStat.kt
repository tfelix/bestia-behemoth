package net.bestia.zone.item.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatValue
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * One stat an [ItemInstance] rolled when it was made. [ordinal] is its place in the roll, so a copy that rolled
 * the same stat twice keeps two rows.
 */
@Entity
@Table(
  name = "item_instance_stat",
  uniqueConstraints = [UniqueConstraint(columnNames = ["item_instance_id", "ordinal"])]
)
class ItemInstanceStat(
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "item_instance_id")
  val itemInstance: ItemInstance,

  @Column(nullable = false)
  val ordinal: Int,

  // VARCHAR rather than a native enum column, so a new ItemStat needs no DDL change under ddl-auto.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  val stat: ItemStat,

  // `value` is a reserved word in H2.
  @Column(name = "stat_value", nullable = false)
  val value: Int
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  fun toStatValue(): StatValue {
    return StatValue(stat, value)
  }
}
