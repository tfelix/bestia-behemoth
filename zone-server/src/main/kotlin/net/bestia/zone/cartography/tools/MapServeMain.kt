package net.bestia.zone.cartography.tools

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.cartography.render.TileInputs
import net.bestia.zone.cartography.tile.MapWorldKey
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.cartography.tile.TileRenderer
import net.bestia.zone.cartography.tile.TileStore
import java.net.InetSocketAddress
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Serves the tile pyramid and a viewer for it, so a whole level can be panned and zoomed in a browser.
 *
 * ```
 * ./gradlew :zone-server:mapServe -Pgenesis          # then open http://localhost:8099
 * ```
 *
 * ### Why this exists rather than a folder of PNGs
 *
 * The failure a tiled map is prone to is *between* tiles, and no single tile shows it. A seam is a line that
 * only appears when two tiles are next to each other at the right zoom, and the only practical way to look for
 * one is to drag the map around and watch the boundaries go by. Opening files one at a time cannot do that;
 * neither can a stitched atlas image, which is drawn as one frame and therefore has no seams by construction.
 *
 * It also renders on demand into the same cache the server uses, which means it doubles as a way to warm a
 * region without baking the whole pyramid, and as a check that the on-demand path produces the same thing the
 * bake does.
 *
 * ### Why the JDK's own HTTP server
 *
 * `jdk.httpserver` is in the JDK, so a development tool costs no dependency and starts in the time it takes to
 * generate a world. The production endpoint is a Spring `@RestController` on zone-server's own port and shares
 * none of this - which is the right split: this serves *without* authentication or fog on purpose, because its
 * job is to show the map as drawn, and adding either would hide the thing being inspected.
 */
object MapServeMain {

  private const val PORT = "--port"
  private const val OUT = "--out"

  private val FLAGS = setOf(PORT, OUT)

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, FLAGS)
    val config = args.config

    val generated = StandardWorld.build(config, params = args.params)
    val inputs = TileInputs.of(generated)
    val key = MapWorldKey.of(generated)
    val store = TileStore(args.file(OUT, "build/map-cache"), key)
    val renderer = ThreadLocal.withInitial { TileRenderer(inputs) }

    val fit = TileId.fitLevel(config.widthMetres)
    val port = args.int(PORT, DEFAULT_PORT)

    val server = HttpServer.create(InetSocketAddress(LOOPBACK, port), BACKLOG)
    server.executor = Executors.newFixedThreadPool(THREADS) { runnable ->
      Thread(runnable, "map-serve").apply { isDaemon = true }
    }

    server.createContext("/") { exchange ->
      when {
        exchange.requestURI.path == "/" -> respond(exchange, "text/html; charset=utf-8", viewerHtml(config.widthMetres, config.heightMetres, fit).toByteArray())
        exchange.requestURI.path.startsWith("/t/") -> tile(exchange, store, renderer, fit)
        else -> respond(exchange, "text/plain", "not found".toByteArray(), 404)
      }
    }

    server.start()

    println(
      "world %.0f x %.0f km, key %s".format(
        Locale.ROOT, config.widthMetres / 1000.0, config.heightMetres / 1000.0, key
      )
    )
    println("cache ${store.directory.absolutePath} holds ${store.measure()}")
    println()
    println("open http://localhost:$port  -  drag to pan, wheel to zoom, levels 0..$fit")
    println("Ctrl-C to stop")

    Thread.currentThread().join()
  }

  /** `/t/{level}/{tx}/{ty}.png`, from the cache or rendered into it. */
  private fun tile(exchange: HttpExchange, store: TileStore, renderer: ThreadLocal<TileRenderer>, fit: Int) {
    val parts = exchange.requestURI.path.removePrefix("/t/").removeSuffix(".png").split('/')
    if (parts.size != 3) {
      respond(exchange, "text/plain", "expected /t/{level}/{tx}/{ty}.png".toByteArray(), 400)
      return
    }

    val id = try {
      TileId(parts[0].toInt(), parts[1].toLong(), parts[2].toLong())
    } catch (e: Exception) {
      respond(exchange, "text/plain", "bad tile id: ${e.message}".toByteArray(), 400)
      return
    }

    if (id.level > fit) {
      // Beyond the level where the world fits one tile there is nothing to show, and rendering it would draw
      // a tile of empty void that looks like a bug.
      respond(exchange, "text/plain", "level above $fit".toByteArray(), 404)
      return
    }

    val cached = store.read(id)
    if (cached != null) {
      respond(exchange, "image/png", cached)
      return
    }

    val encoded = renderer.get().encode(id)
    store.write(id, encoded)
    respond(exchange, "image/png", encoded)
  }

  private fun respond(exchange: HttpExchange, contentType: String, body: ByteArray, status: Int = 200) {
    exchange.responseHeaders.add("Content-Type", contentType)
    // The tool exists to look at tiles that are being changed, so a cached one is the last thing wanted here.
    exchange.responseHeaders.add("Cache-Control", "no-store")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
  }

  /**
   * A slippy map in one page, with a coordinate readout.
   *
   * The readout is not decoration. Pointing `mapRender` or `mapInspect` at a place means knowing its world
   * coordinates, and deriving them from a rendered PNG by counting pixels is both tedious and easy to get wrong
   * by a kilometre - which it was, twice, before this existed. Here you hover over the place and read it off.
   */
  private fun viewerHtml(worldWidth: Double, worldHeight: Double, fit: Int) = """
    <!doctype html>
    <meta charset="utf-8">
    <title>bestia map</title>
    <style>
      html, body { margin: 0; height: 100%; background: #1b1b1f; overflow: hidden;
        font: 12px ui-monospace, monospace; color: #ddd; }
      #map { position: absolute; inset: 0; cursor: grab; }
      #map.dragging { cursor: grabbing; }
      #map img { position: absolute; width: 256px; height: 256px; image-rendering: pixelated; }
      #hud { position: absolute; left: 8px; bottom: 8px; z-index: 10; background: #000a;
        padding: 6px 9px; border-radius: 4px; white-space: pre; pointer-events: none; }
    </style>
    <div id="map"></div>
    <div id="hud"></div>
    <script>
      const TILE = ${TileId.TILE_PIXELS};
      const FIT = $fit;
      const WORLD = { w: $worldWidth, h: $worldHeight };

      // Start on the level where the world fits the window, centred on it.
      let level = FIT;
      while (level > 0 && TILE * Math.pow(2, level - 1) >= Math.max(innerWidth, innerHeight)) level--;
      let cx = WORLD.w / 2, cy = WORLD.h / 2;

      const map = document.getElementById('map');
      const hud = document.getElementById('hud');
      const live = new Map();

      const mpp = () => Math.pow(2, level);
      const span = () => TILE * mpp();

      function draw() {
        const m = mpp(), s = span();
        const w = innerWidth, h = innerHeight;

        const fromX = Math.floor((cx - w / 2 * m) / s), toX = Math.floor((cx + w / 2 * m) / s);
        const fromY = Math.floor((cy - h / 2 * m) / s), toY = Math.floor((cy + h / 2 * m) / s);

        const keep = new Set();
        for (let ty = fromY; ty <= toY; ty++) {
          for (let tx = fromX; tx <= toX; tx++) {
            const key = level + '/' + tx + '/' + ty;
            keep.add(key);

            let img = live.get(key);
            if (!img) {
              img = document.createElement('img');
              img.src = '/t/' + level + '/' + tx + '/' + ty + '.png';
              // A tile outside the world is a 404; hiding it beats a broken-image icon on the map.
              img.onerror = () => { img.style.visibility = 'hidden'; };
              live.set(key, img);
              map.appendChild(img);
            }

            // World y grows north and screen y grows down, so the tile's *top* edge is its ty+1 boundary.
            img.style.left = ((tx * s - cx) / m + w / 2) + 'px';
            img.style.top = (h / 2 - ((ty + 1) * s - cy) / m) + 'px';
          }
        }

        for (const [key, img] of live) {
          if (!keep.has(key)) { img.remove(); live.delete(key); }
        }

        hud.textContent = 'L' + level + '  ' + m + ' m/px  centre ' +
          Math.round(cx) + ', ' + Math.round(cy);
      }

      function worldAt(px, py) {
        return [cx + (px - innerWidth / 2) * mpp(), cy + (innerHeight / 2 - py) * mpp()];
      }

      let dragging = null;
      map.addEventListener('mousedown', e => { dragging = [e.clientX, e.clientY]; map.classList.add('dragging'); });
      addEventListener('mouseup', () => { dragging = null; map.classList.remove('dragging'); });
      addEventListener('mousemove', e => {
        if (dragging) {
          cx -= (e.clientX - dragging[0]) * mpp();
          cy += (e.clientY - dragging[1]) * mpp();
          dragging = [e.clientX, e.clientY];
          draw();
        }
        const [wx, wy] = worldAt(e.clientX, e.clientY);
        hud.textContent = 'L' + level + '  ' + mpp() + ' m/px\n' +
          'x ' + Math.round(wx) + '  y ' + Math.round(wy) + '\n' +
          '-Px=' + Math.round(wx) + ' -Py=' + Math.round(wy);
      });

      map.addEventListener('wheel', e => {
        e.preventDefault();
        const next = Math.max(0, Math.min(FIT, level + (e.deltaY > 0 ? 1 : -1)));
        if (next === level) return;

        // Keep the world point under the cursor fixed, so zooming follows what you were looking at.
        const [wx, wy] = worldAt(e.clientX, e.clientY);
        level = next;
        cx = wx - (e.clientX - innerWidth / 2) * mpp();
        cy = wy - (innerHeight / 2 - e.clientY) * mpp();

        // Every level is its own image set, so nothing from the old one can be reused.
        for (const [, img] of live) img.remove();
        live.clear();
        draw();
      }, { passive: false });

      addEventListener('resize', draw);
      draw();
    </script>
  """.trimIndent()

  private const val DEFAULT_PORT = 8099

  /** Loopback only. A development tool with no authentication has no business on another interface. */
  private const val LOOPBACK = "127.0.0.1"

  private const val BACKLOG = 32

  /** Enough to keep a browser's parallel tile fetches busy without competing for every core. */
  private const val THREADS = 4
}
