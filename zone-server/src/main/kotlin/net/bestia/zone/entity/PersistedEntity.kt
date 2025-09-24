package net.bestia.zone.entity

import jakarta.persistence.*

@Entity
@Table(name = "entity")
class PersistedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @OneToMany(mappedBy = "entity", cascade = [CascadeType.ALL], orphanRemoval = true)
  private val components: MutableList<PersistedComponent> = mutableListOf()

  fun addComponent(component: PersistedComponent) {
    components.add(component)
    component.entity = this
  }

  fun removeComponent(component: PersistedComponent) {
    components.remove(component)
    component.entity = null
  }
}

