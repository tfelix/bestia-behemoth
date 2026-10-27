package net.bestia.api.metrics

/**
 * Which part of the API a request was for, as a tag: a closed set, never a raw path and never an id in it. The
 * filter that turns a request away answers before any route template exists, so it classifies the path itself.
 */
enum class ApiRoute {
  MAP_TILE,
  MAP_HABITAT,
  MAP_PLACES,
  MAP_SERVICES,
  MAP_META,
  MAP_FIND,
  EMBLEM,
  MASTERS,
  CATALOG,
  CHAT,
  STANCE,
  PUSH,
  WS,
  OTHER,
  ;

  /** Whether `api.endpoint.*` counts what became of its requests: the routes with something to cache. */
  val cached: Boolean get() = this in CACHED

  companion object {
    private val CACHED = setOf(MAP_TILE, MAP_HABITAT, MAP_PLACES, MAP_SERVICES, MAP_META, EMBLEM)

    private const val PREFIX = "/v1/companion/"
    private val MAP = Regex("""^/v1/companion/masters/[^/]+/map/([a-z]+)(/.*)?$""")

    fun of(path: String): ApiRoute {
      if (!path.startsWith(PREFIX)) {
        return OTHER
      }

      MAP.matchEntire(path)?.let { match ->
        return when (match.groupValues[1]) {
          "t" -> MAP_TILE
          "h" -> MAP_HABITAT
          "p" -> MAP_PLACES
          "s" -> MAP_SERVICES
          "meta" -> MAP_META
          "find" -> MAP_FIND
          else -> OTHER
        }
      }

      val rest = path.removePrefix(PREFIX)
      return when {
        rest.startsWith("emblems/") -> EMBLEM
        rest.startsWith("catalog") -> CATALOG
        rest.startsWith("push/") -> PUSH
        rest == "ws" -> WS
        rest.startsWith("masters") -> when {
          rest.endsWith("/chat") -> CHAT
          rest.endsWith("/stance") -> STANCE
          else -> MASTERS
        }

        else -> OTHER
      }
    }
  }
}
