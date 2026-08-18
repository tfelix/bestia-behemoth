package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.NavNodeKind
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.Aabb
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Draws the macro navigation graph over a map, at any zoom, without stalling the frame.
 *
 * ### Why this needs care at all
 *
 * The other overlays draw features, and a world has a few thousand of those with a handful of vertices each.
 * This draws a *graph*: on the reference 512 km world about two thousand nodes and six thousand edges, and at
 * the architecture document's full 4096 km scale the lattice is bounded but still enormous - a hundred
 * thousand nodes and several hundred thousand edges, every one of which is inside the viewport the moment
 * somebody presses `F` to fit the world. Handed to Java2D as antialiased `Path2D`s that is seconds per frame,
 * and panning becomes impossible precisely at the zoom where you most want to see the network.
 *
 * So three things happen, in this order, and each is cheap:
 *
 * 1. **Cull.** Every edge's world bounds are precomputed once ([edgeBounds]) and tested against the view. At
 *    any zoom short of the whole world this alone removes almost everything.
 * 2. **Simplify.** An edge shorter than a couple of pixels is a dot and is skipped outright. An edge whose
 *    waypoints are closer together than a pixel is drawn as a straight line between its endpoints, because
 *    following the polyline would paint the same pixels several times over. Antialiasing goes off when the
 *    graph is dense, which is where most of Java2D's cost is.
 * 3. **Cap.** Past [MAX_EDGES_PER_FRAME] the frame stops drawing, having spent its budget on the edges worth
 *    seeing: [drawOrder] puts roads, bridges and lanes first, so what a capped whole-world view shows is the
 *    trade network rather than an arbitrary slice of open country. The caller is told what was left out - see
 *    [Stats] - because an overlay that silently draws two thirds of itself is worse than one that says so.
 *
 * Built once per world and held by [WorldScene], not per frame: the index is O(total waypoints) to construct,
 * which is fine at startup and would not be fine on every repaint.
 */
class NavGraphOverlay(private val graph: NavGraph) {

  /** What one frame actually managed to draw, for the status line. */
  class Stats(
    val edgesDrawn: Int,
    val edgesInView: Int,
    val nodesDrawn: Int,
    /** True when the cap stopped the frame before every visible edge was drawn. */
    val capped: Boolean
  ) {
    companion object {
      val NONE = Stats(0, 0, 0, false)
    }
  }

  val isEmpty get() = graph.nodes.isEmpty()

  val nodeCount get() = graph.nodes.size
  val edgeCount get() = graph.edges.size

  /** Edge counts by kind, for the legend to show only the kinds this world has. */
  val edgeCensus: Map<NavEdgeKind, Int> = graph.edges
    .groupingBy { it.kind }
    .eachCount()
    .entries
    .sortedBy { it.key.ordinal }
    .associateTo(LinkedHashMap()) { it.key to it.value }

  /**
   * Flat `[minX, minY, maxX, maxY]` per edge, including its waypoints.
   *
   * A flat `DoubleArray` rather than a list of `Aabb`: this is read once per edge per frame and an object per
   * edge would be a few hundred thousand allocations to walk on a world where the cull matters most.
   */
  private val edgeBounds: DoubleArray by lazy { computeEdgeBounds() }

  /**
   * Edge indices in the order they should be spent: infrastructure first, open country after.
   *
   * This is what makes the cap defensible rather than arbitrary. A whole-world view of a large world cannot
   * draw every edge, and of the two things it could truncate, the road network is the one carrying information
   * at that scale - the wilderness lattice is a fairly even mesh whose absence changes nothing about what the
   * map tells you.
   */
  private val drawOrder: IntArray by lazy {
    graph.edges.indices.sortedBy { if (isStructural(graph.edges[it].kind)) 0 else 1 }.toIntArray()
  }

  private fun isStructural(kind: NavEdgeKind) = when (kind) {
    NavEdgeKind.ROAD, NavEdgeKind.BRIDGE, NavEdgeKind.SEA_LANE, NavEdgeKind.GATE_SPOKE -> true
    NavEdgeKind.WILDERNESS -> false
  }

  private fun computeEdgeBounds(): DoubleArray {
    val bounds = DoubleArray(graph.edges.size * 4)

    for ((index, edge) in graph.edges.withIndex()) {
      val a = graph.nodes[edge.a.value].position
      val b = graph.nodes[edge.b.value].position

      var minX = minOf(a.x, b.x)
      var minY = minOf(a.y, b.y)
      var maxX = maxOf(a.x, b.x)
      var maxY = maxOf(a.y, b.y)

      edge.waypoints?.forEach { point ->
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
      }

      val slot = index * 4
      bounds[slot] = minX
      bounds[slot + 1] = minY
      bounds[slot + 2] = maxX
      bounds[slot + 3] = maxY
    }

    return bounds
  }

  /**
   * Draws the graph into [g] through [view].
   *
   * @param showWilderness whether to draw the open-country lattice at all. Off is a legitimate way to read a
   *   big world: the roads are the part with structure, and the lattice is what there is most of.
   */
  fun draw(g: Graphics2D, view: Viewport, showWilderness: Boolean = true): Stats {
    if (graph.nodes.isEmpty()) return Stats.NONE

    val bounds = view.bounds
    val bufferedBounds = bounds.expanded(view.metresPerPixel * 4.0)

    // Antialiasing is most of Java2D's per-segment cost, and at coarse zoom it buys nothing anyway: a
    // kilometre-long edge crossing three pixels does not need smooth ends. Restored before returning, since
    // the caller's other overlays want it on.
    val previousAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val dense = view.metresPerPixel > ANTIALIAS_BELOW_METRES_PER_PIXEL
    if (dense) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    }

    g.stroke = BasicStroke(if (dense) 1f else 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    var drawn = 0
    var inView = 0
    var capped = false
    // The cache is per-call: the caller's own overlays have set their own colours since the last frame.
    currentColor = null

    try {
      for (index in drawOrder) {
        val edge = graph.edges[index]
        if (!showWilderness && edge.kind == NavEdgeKind.WILDERNESS) continue
        if (!intersects(index, bufferedBounds)) continue

        inView++

        if (drawn >= MAX_EDGES_PER_FRAME) {
          capped = true
          // No `break`: the loop keeps counting what it is not drawing, so the status line can report the
          // real total rather than "at least the cap".
          continue
        }

        if (drawEdge(g, view, index)) drawn++
      }

      val nodesDrawn = drawNodes(g, view, bufferedBounds, showWilderness)

      return Stats(edgesDrawn = drawn, edgesInView = inView, nodesDrawn = nodesDrawn, capped = capped)
    } finally {
      previousAntialiasing?.let { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, it) }
    }
  }

  private fun intersects(edgeIndex: Int, area: Aabb): Boolean {
    val slot = edgeIndex * 4
    return edgeBounds[slot] <= area.maxX &&
        edgeBounds[slot + 2] >= area.minX &&
        edgeBounds[slot + 1] <= area.maxY &&
        edgeBounds[slot + 3] >= area.minY
  }

  /**
   * The colour Java2D is currently set to, so an unchanged one is not set again.
   *
   * `setColor` is not free - it invalidates the cached rendering loop - and [drawOrder] groups edges by class,
   * so consecutive edges usually want the same colour. Skipping the redundant calls is most of the benefit of
   * having sorted them in the first place.
   */
  private var currentColor: Color? = null

  /** @return whether anything was actually painted, so the budget is only spent on visible ink. */
  private fun drawEdge(g: Graphics2D, view: Viewport, index: Int): Boolean {
    val edge = graph.edges[index]
    val a = graph.nodes[edge.a.value].position
    val b = graph.nodes[edge.b.value].position

    val ax = view.screenX(a.x)
    val ay = view.screenY(a.y)
    val bx = view.screenX(b.x)
    val by = view.screenY(b.y)

    // Shorter than a couple of pixels: this is a dot, and its node will be drawn anyway.
    val span = maxOf(Math.abs(bx - ax), Math.abs(by - ay))
    if (span < MIN_EDGE_PIXELS) return false

    val color = colorOf(edge.kind, edge.modes)
    if (color !== currentColor) {
      g.color = color
      currentColor = color
    }

    val waypoints = edge.waypoints
    // Straight when there is nothing to follow, or when following it would repaint the same pixels: waypoint
    // spacing below a pixel means the polyline and the chord are the same picture at several times the cost.
    val waypointPixels = if (waypoints.isNullOrEmpty()) 0.0 else edge.lengthMetres /
        (waypoints.size + 1) / view.metresPerPixel

    if (waypoints.isNullOrEmpty() || waypointPixels < MIN_WAYPOINT_PIXELS) {
      g.drawLine(ax.toInt(), ay.toInt(), bx.toInt(), by.toInt())
      return true
    }

    // Integer segments rather than a Path2D: at this point the line is a chain of short straight pieces and
    // `drawLine` avoids building and stroking a shape per edge.
    var previousX = ax.toInt()
    var previousY = ay.toInt()
    for (point in waypoints) {
      val x = view.screenX(point.x).toInt()
      val y = view.screenY(point.y).toInt()
      // Skip a segment that lands on the pixel it started from - common on a long edge at coarse zoom.
      if (x != previousX || y != previousY) {
        g.drawLine(previousX, previousY, x, y)
        previousX = x
        previousY = y
      }
    }
    g.drawLine(previousX, previousY, bx.toInt(), by.toInt())

    return true
  }

  /**
   * Nodes as small squares, sized by what they are.
   *
   * Squares rather than the stroked circles [MapRenderer] uses for feature markers, and filled with
   * `fillRect` rather than a shape: a hub deserves to be findable, and there are tens of thousands of
   * wilderness nodes whose only job is to show that the lattice is there. The wilderness ones vanish entirely
   * once they would be closer together than a few pixels, which is the zoom at which they stop being dots and
   * start being a wash.
   */
  private fun drawNodes(g: Graphics2D, view: Viewport, area: Aabb, showWilderness: Boolean): Int {
    // A lattice node is spaced kilometres from its neighbours; below this it is not a dot any more.
    val latticeVisible = showWilderness && view.metresPerPixel < WILDERNESS_NODE_BELOW_METRES_PER_PIXEL
    var drawn = 0

    for (node in graph.nodes) {
      if (node.kind == NavNodeKind.WILDERNESS && !latticeVisible) continue

      val position = node.position
      if (position.x < area.minX || position.x > area.maxX) continue
      if (position.y < area.minY || position.y > area.maxY) continue

      val size = if (node.kind == NavNodeKind.WILDERNESS) 1 else HUB_NODE_PIXELS
      val x = view.screenX(position.x).toInt() - size / 2
      val y = view.screenY(position.y).toInt() - size / 2

      g.color = colorOf(node.kind, node.standing)
      g.fillRect(x, y, size, size)
      drawn++
    }

    return drawn
  }

  companion object {

    /**
     * Most edges one frame will draw.
     *
     * Chosen against what a screen can actually show rather than against a timing: a 1080p map is about two
     * million pixels, and twenty thousand edges already paint more line than that at whole-world zoom, so
     * beyond this the extra work is literally invisible. It also keeps the pathological case - the full-scale
     * world, fitted to the window - to a fixed cost rather than one that grows with the world.
     */
    const val MAX_EDGES_PER_FRAME = 20_000

    /** Below this many pixels of extent an edge is not drawn; its nodes carry the information instead. */
    private const val MIN_EDGE_PIXELS = 2.0

    /**
     * Waypoints closer together than this are not followed - the chord is the same picture.
     *
     * Four pixels rather than one, and the difference is most of a frame. A generated edge carries a waypoint
     * every couple of hundred metres, so on the reference world at whole-world zoom each one is about two
     * pixels from the last and wanders a *fraction* of a pixel off the straight line: following it drew seven
     * segments per edge to paint what one segment paints. Below this the polyline is not detail, it is the same
     * line drawn several times.
     */
    private const val MIN_WAYPOINT_PIXELS = 4.0

    /** Coarser than this, antialiasing costs a lot and shows nothing. Roughly "a chunk per pixel". */
    private const val ANTIALIAS_BELOW_METRES_PER_PIXEL = 40.0

    /**
     * Coarser than this, the wilderness lattice is not drawn as nodes at all.
     *
     * Set against the default 8 km lattice spacing: at 400 m/px neighbours are twenty pixels apart, which
     * still reads as a mesh. Past it they crowd into a texture that hides the roads underneath.
     */
    private const val WILDERNESS_NODE_BELOW_METRES_PER_PIXEL = 400.0

    private const val HUB_NODE_PIXELS = 4

    /**
     * The lattice colours, and **opaque on purpose**.
     *
     * These carried an alpha at first, which reads slightly better over a busy biome map and cost a great deal
     * more than it looked: a translucent colour puts Java2D on its `SrcOver` blending path, which is per-pixel
     * work rather than a fill, and at whole-world zoom the lattice is most of the ink on screen. Removing the
     * alpha was worth about a third of the frame on the reference world. The hues are muted instead, which buys
     * the same legibility for nothing.
     */
    /**
     * The infrastructure hues, borrowed from the matching features so the eye groups them.
     *
     * Constants rather than freshly built per call, and not only to save the allocation: [drawEdge] compares
     * the colour it wants against the one already set by *identity*, which needs the same instance back every
     * time or the comparison never matches and the state change happens anyway.
     */
    private val ROAD = Color(230, 170, 90)
    private val BRIDGE = Color(255, 130, 60)
    private val SEA_LANE = Color(255, 225, 175)
    private val GATE_SPOKE = Color(255, 250, 230)

    private val WILDERNESS_WALK = Color(96, 150, 104)
    private val WILDERNESS_CLIMB = Color(168, 146, 88)
    private val WILDERNESS_FORD = Color(74, 138, 190)

    private val NODE_SETTLEMENT = Color(255, 60, 60)
    private val NODE_RUIN = Color(150, 130, 110)
    private val NODE_CAVE = Color(235, 215, 255)
    private val NODE_WILDERNESS = Color(150, 210, 160)

    /**
     * Edge colour, by what it is and what it demands.
     *
     * The infrastructure kinds borrow their hues from the features they were read off - see
     * [MapRenderer.colorOf] - so a road in the nav overlay and a road in the feature overlay are the same
     * orange and the eye groups them. Open country gets a green of its own, and a hop that fords water is
     * drawn in water's blue instead: "this route gets its feet wet" is the single most useful thing the
     * overlay can say about a wilderness edge, since it is what a creature that cannot swim will be refused.
     */
    fun colorOf(kind: NavEdgeKind, modes: Set<MovementMode>): Color = when (kind) {
      NavEdgeKind.ROAD -> ROAD
      NavEdgeKind.BRIDGE -> BRIDGE
      NavEdgeKind.SEA_LANE -> SEA_LANE
      NavEdgeKind.GATE_SPOKE -> GATE_SPOKE
      NavEdgeKind.WILDERNESS -> when {
        MovementMode.SWIM in modes -> WILDERNESS_FORD
        // Steep but passable. Warmer than the flat lattice, so a mountain pass stands out from a meadow.
        MovementMode.CLIMB in modes -> WILDERNESS_CLIMB
        else -> WILDERNESS_WALK
      }
    }

    /**
     * Node colour, by what put it there.
     *
     * Hubs borrow the corresponding feature's hue for the same reason the edges do. A settlement history left
     * empty is drawn muted rather than red, because a route to a ruin is still a route and worth seeing, but
     * it is not somewhere anybody lives.
     */
    fun colorOf(kind: NavNodeKind, standing: Boolean = true): Color = when (kind) {
      NavNodeKind.SETTLEMENT -> if (standing) NODE_SETTLEMENT else NODE_RUIN
      NavNodeKind.GATE -> GATE_SPOKE
      NavNodeKind.BRIDGE_APPROACH -> BRIDGE
      NavNodeKind.CAVE_ENTRANCE -> NODE_CAVE
      NavNodeKind.WILDERNESS -> NODE_WILDERNESS
    }
  }
}
