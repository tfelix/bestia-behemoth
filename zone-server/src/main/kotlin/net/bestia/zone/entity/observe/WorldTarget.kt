package net.bestia.zone.entity.observe

/**
 * A thing or a person in the world, named so that the name outlives the entity: an entity id is new on every
 * boot. [key] is whatever names one of its [kind] for good.
 */
data class WorldTarget(val kind: Kind, val key: String) {

  enum class Kind { PROP, POI, TOWNSFOLK }

  companion object {

    /** [propId] of a `WorldObjectIdentity`. */
    fun prop(propId: Long): WorldTarget {
      return WorldTarget(Kind.PROP, propId.toString())
    }

    /** By kind name, so a reordered catalogue cannot point at another POI. */
    fun poi(kindName: String, instanceId: Int): WorldTarget {
      return WorldTarget(Kind.POI, "$kindName/$instanceId")
    }

    /** [identity] of a `TownsfolkIdentity`. */
    fun townsfolk(identity: Long): WorldTarget {
      return WorldTarget(Kind.TOWNSFOLK, identity.toString())
    }
  }
}
