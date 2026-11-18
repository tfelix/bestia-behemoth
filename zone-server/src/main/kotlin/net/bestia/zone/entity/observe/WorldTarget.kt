package net.bestia.zone.entity.observe

/**
 * A thing or a person in the world, named so that the name outlives the entity: an entity id is new on every
 * boot. [key] is whatever names one of its [kind] for good.
 */
data class WorldTarget(val kind: Kind, val key: String) {

  enum class Kind { PROP, POI, TOWNSFOLK, GRAVE, CACHE, SETTLEMENT }

  companion object {

    /** [propId] of a `WorldObjectIdentity`. */
    fun prop(propId: Long): WorldTarget {
      return WorldTarget(Kind.PROP, propId.toString())
    }

    /** By kind name, so a reordered catalogue cannot point at another POI. */
    fun poi(kindName: String, instanceId: Int): WorldTarget {
      return WorldTarget(Kind.POI, "$kindName/$instanceId")
    }

    /** The named grave of a figure of the chronicle, by the figure's index. */
    fun grave(figure: Int): WorldTarget {
      return WorldTarget(Kind.GRAVE, figure.toString())
    }

    /** A treasure cache, by the id the world generator gave it. */
    fun cache(cacheId: Long): WorldTarget {
      return WorldTarget(Kind.CACHE, cacheId.toString())
    }

    /** A settlement, by its index in the chronicle. */
    fun settlement(index: Int): WorldTarget {
      return WorldTarget(Kind.SETTLEMENT, index.toString())
    }

    /** [identity] of a `TownsfolkIdentity`. */
    fun townsfolk(identity: Long): WorldTarget {
      return WorldTarget(Kind.TOWNSFOLK, identity.toString())
    }
  }
}
