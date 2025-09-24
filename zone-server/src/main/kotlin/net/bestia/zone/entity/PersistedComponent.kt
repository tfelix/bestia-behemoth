package net.bestia.zone.entity

import jakarta.persistence.*

@Entity
@Table(name = "entity_component")
class PersistedComponent(
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