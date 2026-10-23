package net.bestia.api.metrics

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApiRouteTest {

  @Test
  fun `a path becomes a route without keeping any id in it`() {
    val routes = mapOf(
      "/v1/companion/masters/5/map/t/3/1/2.png" to ApiRoute.MAP_TILE,
      "/v1/companion/masters/5/map/h/1/2/3/-4.png" to ApiRoute.MAP_HABITAT,
      "/v1/companion/masters/5/map/p/3/-1/2.json" to ApiRoute.MAP_PLACES,
      "/v1/companion/masters/5/map/s/2/1/1" to ApiRoute.MAP_SERVICES,
      "/v1/companion/masters/5/map/meta" to ApiRoute.MAP_META,
      "/v1/companion/emblems/${"ab".repeat(32)}.png" to ApiRoute.EMBLEM,
      "/v1/companion/masters" to ApiRoute.MASTERS,
      "/v1/companion/masters/5/inventory" to ApiRoute.MASTERS,
      "/v1/companion/masters/5/map/../bestias" to ApiRoute.MASTERS,
      "/v1/companion/masters/5/chat" to ApiRoute.CHAT,
      "/v1/companion/masters/5/bestias/7/stance" to ApiRoute.STANCE,
      "/v1/companion/catalog" to ApiRoute.CATALOG,
      "/v1/companion/push/subscriptions" to ApiRoute.PUSH,
      "/v1/companion/ws" to ApiRoute.WS,
      "/v1/companion/something-new" to ApiRoute.OTHER,
      "/elsewhere" to ApiRoute.OTHER,
    )

    for ((path, route) in routes) {
      assertEquals(route, ApiRoute.of(path), path)
    }
  }
}
