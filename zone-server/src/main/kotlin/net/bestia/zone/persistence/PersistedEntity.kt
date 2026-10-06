package net.bestia.zone.persistence

import jakarta.persistence.*
import java.time.Instant

/**
 * Generic persistence row for an ECS entity that does not have its own dedicated table
 * (mobs, dropped items, structures). Player masters/bestias are NOT stored here — they
 * keep their own relational tables. An entity is stored as a small set of serialized
 * [PersistedComponent] blobs plus the routing metadata below.
 */
@Entity
@Table(
  name = "entity",
  indexes = [Index(name = "idx_entity_entity_id", columnList = "entityId", unique = true)]
)
class PersistedEntity(
  /** The live ECS entity id (Snowflake). Reused via `world.createEntity(id)` on reload so references stay stable. */
  @Column(nullable = false)
  var entityId: Long = 0,

  /** The [net.bestia.zone.persistence.EntityPersister.kind] that owns this row, used to route loading. */
  @Column(nullable = false, length = 32)
  var kind: String = "",
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()

  @OneToMany(mappedBy = "entity", cascade = [CascadeType.ALL], orphanRemoval = true)
  val components: MutableList<PersistedComponent> = mutableListOf()

  fun addComponent(component: PersistedComponent) {
    components.add(component)
    component.entity = this
  }

  /**
   * Makes [data] the entity's one component blob. The existing row of that [type] is updated in place, so a
   * periodic save is one UPDATE rather than a DELETE and an INSERT.
   */
  fun writeComponent(type: String, data: String) {
    val existing = components.firstOrNull { it.type == type }
    components.retainAll { it === existing }

    if (existing != null) {
      existing.data = data
    } else {
      addComponent(PersistedComponent(type = type, data = data))
    }
  }
}
