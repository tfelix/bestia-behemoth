package net.bestia.zone.entity

import jakarta.persistence.*

/**
 * A single serialized component blob belonging to a [PersistedEntity]. [type] is a stable
 * discriminator (e.g. the snapshot kind) the owning persister uses to deserialize [data],
 * which holds the JSON of the minimal mutable state.
 */
@Entity
@Table(name = "entity_component")
class PersistedComponent(
  @Column(nullable = false, length = 64)
  var type: String,

  @Lob
  @Column(nullable = false)
  var data: String
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "entityId", foreignKey = ForeignKey(name = "fk_component_entity"))
  var entity: PersistedEntity? = null

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is PersistedComponent) return false
    return id == o.id
  }

  override fun hashCode(): Int {
    return javaClass.hashCode()
  }
}
