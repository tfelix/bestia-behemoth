using System.Collections.Generic;
using System.Diagnostics;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Scatters decorative grass over the ground that is drawn as grass.
  /// </summary>
  /// <remarks>
  /// <b>Not the same thing as the ground cover, and the difference is who owns it.</b> A herb, a shrub and a
  /// reed are server entities with ids, health and a <c>collect</c> block - see <c>GroundCoverScatter</c> and
  /// <see cref="StaticEntityRenderer"/> - and they are scattered on a three-metre lattice because every one of
  /// them costs a <c>world.createEntity</c> per column a player holds. That budget is what
  /// <c>GroundCoverParams.litterGain</c> spends, and it is why a meadow of them reads as a dozen plants in a
  /// field rather than as a field.
  ///
  /// <para>
  /// This is the field. It is drawn and nothing else: no ids, no server, no clicks, no collision, and no
  /// message on the wire. So it can run at whatever density looks right, and the only thing it costs is
  /// triangles.
  /// </para>
  ///
  /// <para>
  /// <b>Scattered on the terrain mesh rather than on the voxels under it.</b> The mesher already decided where
  /// the ground is and what it is made of: <see cref="ChunkSurface.Vertices"/> is the drawn surface, and
  /// <see cref="ChunkSurface.SlotWeights0"/> carries how much of each vertex is
  /// <see cref="BlockAppearance.SurfaceSlot.Grass"/>. Sampling that is exact by construction - a blade cannot
  /// end up hovering over ground the mesher put somewhere else, and grass fades out exactly where the texture
  /// does, because it is reading the same number the terrain shader is.
  /// </para>
  ///
  /// <para>
  /// <b>The unit of drawing is a cell, not a chunk.</b> Each chunk's grass is split across a world-aligned
  /// <see cref="CellMetres"/> grid, and each cell is one <see cref="MultiMesh"/> whose density is set from that
  /// cell's own distance to the camera. Doing it per chunk instead is the obvious thing and it does not work:
  /// a chunk is 32 m across, so one whose near corner is under the player's feet also reaches past the fade
  /// radius, and measuring it by its nearest point draws the whole thousand square metres at full density.
  /// Measured on nine by nine grassy chunks, that was 2.1 M triangles where cells make it under a million.
  /// </para>
  ///
  /// <para>
  /// <b>Density falls off through <see cref="MultiMesh.VisibleInstanceCount"/>.</b> That property truncates the
  /// instance buffer, so a cell's grass is a prefix of its own transforms. The scatter shuffles each cell
  /// before uploading it - which is the entire reason it does - so a prefix is a uniform random subset of the
  /// cell rather than whichever triangles the mesher happened to emit first. Changing the count is one integer
  /// per cell per frame, with no rebuild and no reupload.
  /// </para>
  ///
  /// <para>
  /// Shadows are off. A shadow pass over tens of thousands of instances costs about what the colour pass does,
  /// and grass is the one thing in the world whose shadow nobody can pick out from the grass next to it.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class TerrainGrass : Node3D
  {
    /// <summary>Clumps per square metre of fully grassy, flat ground.</summary>
    /// <remarks>
    /// The knob to turn first, and the one that costs. A clump is 130 triangles, so this multiplied by the area
    /// inside <see cref="FullDensityMetres"/> plus the tail in the taper is the whole bill.
    ///
    /// <para>
    /// Ground that is only partly grass gets proportionally less, because the weight it is multiplied by is the
    /// slot weight the terrain shader blends with - so a dune with green patches on it grows grass on the
    /// patches and nowhere else, without this having to know what a dune is.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,20,0.1")] public float Density { get; set; } = 5.0f;

    /// <summary>Height of a clump in metres, before <see cref="HeightSpread"/>.</summary>
    /// <remarks>
    /// The clump is scaled uniformly, so this is the model's whole scale and not only its height: at 1.2 m one
    /// spans about 1.53 m of ground where at 0.4 m it spanned 0.51 m. Nine times the ground per clump for the
    /// same 130 triangles, which is why raising this is by far the cheapest way to make the field read as
    /// full - see <see cref="Density"/>, which can come down to pay for it.
    ///
    /// <para>
    /// It is <b>taller than a herb</b>, which stands at 0.45 m, so the collectible ground cover is now inside
    /// the field rather than above it. That is deliberate and known: the plants worth picking are to be told
    /// apart by having their own model, not by being the tallest thing around.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.05,4,0.01")] public float Height { get; set; } = 1.2f;

    /// <summary>Half-width of the size spread around <see cref="Height"/>, as a share of it.</summary>
    [Export(PropertyHint.Range, "0,1,0.01")] public float HeightSpread { get; set; } = 0.35f;

    /// <summary>How far from the camera, in metres, grass is drawn at its full <see cref="Density"/>.</summary>
    [Export(PropertyHint.Range, "0,200,1")] public float FullDensityMetres { get; set; } = 12.0f;

    /// <summary>How far from the camera, in metres, the last clump goes out.</summary>
    /// <remarks>
    /// Measured to the <i>nearest</i> point of a cell rather than to its middle, so a cell the camera is
    /// standing at the edge of is at full density rather than at whatever a half-cell offset works out to.
    /// </remarks>
    [Export(PropertyHint.Range, "0,400,1")] public float FadeOutMetres { get; set; } = 40.0f;

    /// <summary>
    /// Edge of one LOD cell, in metres. The granularity the density can vary at, and one draw call each.
    /// </summary>
    /// <remarks>
    /// The trade is entirely between draw calls and wasted triangles, and it is not close at either end. Coarse
    /// cells draw a whole cell at the density of its nearest corner; fine cells draw a few hundred multimeshes.
    /// Eight metres puts about a hundred cells inside the fade radius and keeps the step between one cell's
    /// density and its neighbour's small enough not to read as a seam.
    ///
    /// <para>
    /// Aligned to the world rather than to each chunk, so a cell straddling a chunk boundary is drawn as two
    /// halves at the same density instead of as two cells offset from one another.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "2,64,1")] public float CellMetres { get; set; } = 8.0f;

    /// <summary>
    /// Ceiling on the instances built for one chunk, whatever <see cref="Density"/> asks for.
    /// </summary>
    /// <remarks>
    /// A memory bound rather than a rendering one - the buffers are built for every chunk the terrain holds,
    /// including the ones beyond <see cref="FadeOutMetres"/> that will draw none of them, because the
    /// <see cref="ChunkSurface"/> they are scattered from is handed over once at install and is not kept.
    ///
    /// <para>
    /// A backstop rather than a knob. It is applied by scaling <see cref="Density"/> down for the chunk that
    /// would exceed it, so a capped chunk is uniformly thinner rather than bare on one side - but a chunk that
    /// hits it is drawn at a density its neighbours are not, which is a seam. The default is above what a
    /// wholly grassy 32 m chunk asks for at the default density, so nothing reaches it in ordinary play.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,40000,100")] public int MaxPerChunk { get; set; } = 8000;

    /// <summary>
    /// How upright ground has to be to grow grass, as the vertical component of its normal.
    /// </summary>
    /// <remarks>
    /// 0.6 is about 53 degrees. Grass is drawn standing straight up whatever it grows on, which is right for a
    /// slope and absurd for a wall: on a cliff face the blades would stand out of it sideways, and the
    /// surface-nets mesh has plenty of near-vertical triangles where a terrace steps down.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.01")] public float MinUpright { get; set; } = 0.6f;

    /// <summary>
    /// How long, in milliseconds, one frame may spend scattering newly arrived chunks.
    /// </summary>
    /// <remarks>
    /// Scattering a wholly grassy 32 m chunk at the default density measures at about 3 ms, and
    /// <c>TerrainRenderer.InstallsPerFrame</c> installs two chunks a frame - so without a budget, walking into
    /// a meadow would spend 6 ms a frame on grass alone, on top of the mesh upload that prompted it. This is
    /// what makes <see cref="Density"/> safe to turn up: raising it makes the field arrive over more frames
    /// rather than making those frames longer.
    ///
    /// <para>
    /// Checked between chunks and not within one, so a single chunk can overrun it. A chunk is the smallest
    /// thing that can be scattered at all - the alternative is a half-grassed chunk on screen - and the cap
    /// on <see cref="MaxPerChunk"/> is what bounds how far one can overrun by.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.1,16,0.1")] public float BuildBudgetMillis { get; set; } = 1.5f;

    /// <summary>The mesh one clump is drawn with, and the material to draw it with.</summary>
    /// <remarks>
    /// The larger of the two grass meshes, which is the cheaper one per square metre covered: a dozen blades
    /// spanning 2.85 m for 130 triangles, against <c>grass2</c>'s single tuft spanning 0.6 m for 72. Both are
    /// what <see cref="PropAppearance"/> gives the ground cover, so the field and the plants standing in it are
    /// the same art at different sizes.
    /// </remarks>
    private const string ClumpPath = "res://Game/Terrain/Grass/grass.res";

    private const string MaterialPath = "res://Game/Terrain/Grass/grass.tres";

    /// <summary>Height, in metres, that <see cref="ClumpPath"/>'s mesh stands at above its own origin.</summary>
    /// <remarks><see cref="PropAppearance"/> carries the same number for the same mesh, and for the same reason.</remarks>
    private const float ClumpNaturalHeight = 2.2391f;

    /// <summary>One cell's worth of grass: a multimesh, and the box its clumps stand in.</summary>
    private sealed class Patch
    {
      internal MultiMeshInstance3D Node;
      internal MultiMesh Multi;

      /// <summary>The box the LOD measures its distance to.</summary>
      internal Vector3 Min;
      internal Vector3 Max;

      internal int Total;

      /// <summary>What <see cref="MultiMesh.VisibleInstanceCount"/> was last set to. -1 until the first pass.</summary>
      internal int Visible = -1;
    }

    /// <summary>Chunk -> its cells, so a re-mesh or a withdrawal takes all of them together.</summary>
    private readonly Dictionary<ChunkKey, List<Patch>> _patches = new();

    /// <summary>
    /// Chunks whose ground has arrived but whose grass has not been scattered yet.
    /// </summary>
    /// <remarks>
    /// Holds the <see cref="ChunkSurface"/> itself, which is the one real cost of deferring: those arrays are
    /// otherwise dropped the moment <see cref="TerrainRenderer"/> has uploaded them, and a login queues the
    /// whole view volume at once. It is transient - the queue drains at
    /// <see cref="BuildBudgetMillis"/> a frame and is empty within a second or two of the terrain settling.
    ///
    /// <para>
    /// A dictionary rather than a queue, so that a chunk re-meshed while still waiting replaces its own entry
    /// instead of being scattered twice from two versions of the same ground.
    /// </para>
    /// </remarks>
    private readonly Dictionary<ChunkKey, ChunkSurface> _pending = new();

    private Godot.Mesh _clump;
    private Material _material;
    private bool _loaded;

    /// <summary>
    /// Replaces this chunk's grass from the terrain surface that was just drawn for it.
    /// </summary>
    /// <remarks>
    /// Called by <see cref="TerrainRenderer"/> on every install, which means a re-mesh takes its grass with it.
    /// That is the opposite of how the props are wired - <c>game.gd</c> keeps those out of the terrain's
    /// lifecycle deliberately, because nothing standing on the ground should be torn down when the ground is
    /// patched - and it is right here for the mirror-image reason: this grass is not standing on the surface,
    /// it is a sample of it, so a surface that changed means a sample that is wrong.
    ///
    /// <para>
    /// The scatter is seeded from the chunk key alone, so a re-mesh puts every clump back where it was. Only
    /// the ground that actually changed moves.
    /// </para>
    ///
    /// <para>
    /// The old grass goes at once and the new grass is queued - see <see cref="BuildBudgetMillis"/> - so a
    /// re-meshed chunk is briefly bare. That is the right way round: the alternative is grass left standing on
    /// ground that has moved out from under it, and what usually prompts a re-mesh is a few voxels being dug.
    /// </para>
    /// </remarks>
    public void Build(ChunkKey key, ChunkSurface terrain)
    {
      Remove(key);

      if (terrain == null || terrain.IsEmpty || Density <= 0.0f || MaxPerChunk <= 0)
      {
        return;
      }

      var vertices = terrain.Vertices;
      var normals = terrain.Normals;
      var indices = terrain.Indices;
      var weights = terrain.SlotWeights0;

      // No weights, no idea which of this ground is grass. A surface built before the slot channels existed, or
      // by a test that did not fill them, gets no grass rather than grass everywhere.
      if (weights == null || vertices == null || normals == null ||
          weights.Length != vertices.Length * 4 || normals.Length != vertices.Length)
      {
        return;
      }

      if (!Load())
      {
        return;
      }

      // Queued rather than scattered here. See BuildBudgetMillis: this is called from the terrain's install
      // path, twice a frame, and a wholly grassy chunk is milliseconds of work.
      _pending[key] = terrain;
    }

    /// <summary>Scatters one queued chunk and puts its cells on screen.</summary>
    private void Raise(ChunkKey key, ChunkSurface terrain)
    {
      var cells = Scatter(key, terrain.Vertices, terrain.Normals, terrain.Indices, terrain.SlotWeights0);
      if (cells.Count == 0)
      {
        return;
      }

      var patches = new List<Patch>(cells.Count);
      var eye = Eye();

      foreach (var (cell, transforms) in cells)
      {
        var patch = Install(key, cell, transforms);
        patches.Add(patch);

        // Retuned here rather than left for the next frame, so that a chunk streaming in a hundred metres away
        // does not draw one frame of full-density grass before being told to stop.
        if (eye.HasValue)
        {
          Retune(patch, eye.Value);
        }
      }

      _patches[key] = patches;
    }

    /// <summary>
    /// Scatters as many queued chunks as this frame's budget allows, nearest to the camera first.
    /// </summary>
    /// <remarks>
    /// Nearest first because the queue is longest at login, when the whole view volume arrives at once, and
    /// the order it drains in is the order the field appears in. Ground under the player's feet is what they
    /// are looking at; ground out at the fade radius is a handful of clumps whenever it gets there.
    /// </remarks>
    private void Drain(Vector3? eye)
    {
      if (_pending.Count == 0)
      {
        return;
      }

      var budget = Mathf.Max(BuildBudgetMillis, 0.1f);
      var watch = Stopwatch.StartNew();

      while (_pending.Count > 0 && watch.Elapsed.TotalMilliseconds < budget)
      {
        var next = Nearest(eye);
        var terrain = _pending[next];
        _pending.Remove(next);

        Raise(next, terrain);
      }
    }

    /// <summary>The queued chunk closest to the camera, or an arbitrary one when there is no camera.</summary>
    /// <remarks>
    /// A scan rather than a structure kept in order. The queue is at most the view volume - a couple of
    /// hundred entries at login and none once it has settled - and this runs a handful of times a frame, so a
    /// heap maintained across every install would cost more than this costs to walk.
    /// </remarks>
    private ChunkKey Nearest(Vector3? eye)
    {
      var best = default(ChunkKey);
      var bestDistance = float.MaxValue;
      var first = true;

      foreach (var (key, terrain) in _pending)
      {
        if (first)
        {
          best = key;
          first = false;
        }

        if (!eye.HasValue || terrain.Vertices.Length == 0)
        {
          break;
        }

        // The first vertex rather than the surface's middle: a chunk is 32 m across and this is ordering the
        // queue, not measuring it, so any point inside one separates the near from the far.
        var distance = terrain.Vertices[0].DistanceSquaredTo(eye.Value);
        if (distance < bestDistance)
        {
          bestDistance = distance;
          best = key;
        }
      }

      return best;
    }

    /// <summary>Turns one cell's transforms into a multimesh under this node.</summary>
    private Patch Install(ChunkKey key, long cell, List<Transform3D> transforms)
    {
      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = _clump,
        InstanceCount = transforms.Count
      };

      // Filled through the buffer rather than by SetInstanceTransform per instance, which is one call across
      // the managed boundary each: at a couple of thousand clumps a chunk and two chunks installed a frame,
      // that was the single most expensive thing about streaming into a meadow. Same data, one call.
      //
      // The layout is the 3x4 matrix in row-major order - three basis rows of three, each followed by one
      // component of the origin. `Basis`'s C# indexer addresses *columns*, so the row-major read is
      // transposed: element [row, column] is `basis[column][row]`.
      var buffer = new float[transforms.Count * 12];

      var min = transforms[0].Origin;
      var max = min;

      for (var i = 0; i < transforms.Count; i++)
      {
        var basis = transforms[i].Basis;
        var origin = transforms[i].Origin;
        var at = i * 12;

        buffer[at + 0] = basis[0].X;
        buffer[at + 1] = basis[1].X;
        buffer[at + 2] = basis[2].X;
        buffer[at + 3] = origin.X;
        buffer[at + 4] = basis[0].Y;
        buffer[at + 5] = basis[1].Y;
        buffer[at + 6] = basis[2].Y;
        buffer[at + 7] = origin.Y;
        buffer[at + 8] = basis[0].Z;
        buffer[at + 9] = basis[1].Z;
        buffer[at + 10] = basis[2].Z;
        buffer[at + 11] = origin.Z;

        min = min.Min(origin);
        max = max.Max(origin);
      }

      multi.Buffer = buffer;

      var node = new MultiMeshInstance3D
      {
        Name = $"Grass_{key.X}_{key.Y}_{cell}",
        Multimesh = multi,
        MaterialOverride = _material,
        CastShadow = GeometryInstance3D.ShadowCastingSetting.Off,

        // The same margin StaticEntityRenderer's batches carry, for the same reason: the wind moves vertices
        // that Godot's bounds know nothing about.
        ExtraCullMargin = 1.2f
      };

      AddChild(node);

      return new Patch
      {
        Node = node,
        Multi = multi,
        Min = min,

        // Grown by the tallest clump, because the transforms only record where each one stands. A box that
        // stopped at the ground would call a cell below the camera further away than its blades are.
        Max = max + new Vector3(0.0f, Height * (1.0f + HeightSpread), 0.0f),
        Total = transforms.Count
      };
    }

    /// <summary>
    /// Places clumps over the grassy part of one chunk's surface, grouped by cell and shuffled within each.
    /// </summary>
    /// <remarks>
    /// Area-weighted per triangle and slot-weighted per triangle, with the fraction left over carried into the
    /// next one. The carry is what makes a low density work at all: a triangle of a quarter of a square metre
    /// wants 0.6 of a clump, and rounding that to zero everywhere would leave thin ground bare no matter how
    /// much of it there was.
    ///
    /// <para>
    /// <see cref="BlockAppearance.SurfaceSlot.DryGrass"/> is deliberately not included, though bunchgrass is
    /// still grass. It is a different colour, and this draws one material - so a dune would come out the green
    /// of a meadow. Giving it its own tint is a per-instance colour on the multimesh and a <c>COLOR</c> read in
    /// the shader, which is worth doing and is not done here.
    /// </para>
    /// </remarks>
    private Dictionary<long, List<Transform3D>> Scatter(
      ChunkKey key, Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      var cells = new Dictionary<long, List<Transform3D>>();

      // Seeded from the chunk alone - not from the mesh - so that a re-mesh, a reconnect or a second client
      // standing in the same field all scatter the same grass. Mixed rather than concatenated because
      // neighbouring chunks differ in one low bit, and a xorshift fed adjacent seeds starts out correlated.
      var rng = new Rng(Mix((uint)(key.X * 73856093) ^ (uint)(key.Y * 19349663) ^ (uint)(key.Z * 83492791)));

      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var edge = Mathf.Max(CellMetres, 0.5f);
      var carry = 0.0f;

      // Two passes, because the cap has to be applied as a density and not as a stopping point. Walking until
      // MaxPerChunk clumps had been placed would fill the chunk in the order the mesher emitted its triangles
      // and then stop, which leaves one side of a capped chunk bare - a far more visible failure than the
      // uniformly thinner grass that scaling gives, and one that looks like a bug in the scatter rather than
      // like a budget.
      var density = Density;
      var grassy = GrassyArea(vertices, normals, indices, weights);

      if (grassy <= 0.0f)
      {
        return cells;
      }

      if (grassy * density > MaxPerChunk)
      {
        density = MaxPerChunk / grassy;
      }

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        // The vertex normals rather than the face's own cross product, because the sign of that depends on a
        // winding this code should not have to know, and getting it backwards would grow grass on the
        // undersides of overhangs and nowhere else.
        var upright = (normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f;
        if (upright < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        var ab = vertices[ib] - a;
        var ac = vertices[ic] - a;

        var area = ab.Cross(ac).Length() * 0.5f;
        if (area <= 0.0f)
        {
          continue;
        }

        carry += area * grass * density;

        var wanted = (int)carry;
        carry -= wanted;

        for (var n = 0; n < wanted; n++)
        {
          // Uniform over the triangle: the two rolls address the enclosing parallelogram, and folding the far
          // half back over the diagonal is what keeps it uniform rather than piling points into one corner.
          var u = rng.Float();
          var v = rng.Float();
          if (u + v > 1.0f)
          {
            u = 1.0f - u;
            v = 1.0f - v;
          }

          var at = a + ab * u + ac * v;

          var height = Height * (1.0f + (rng.Float() - 0.5f) * 2.0f * HeightSpread);
          var scale = height / ClumpNaturalHeight;

          // Uniform, so the basis stays a rotation times a scalar - which is the assumption grass.gdshader
          // inverts the model matrix under. Grass is drawn straight up whatever it grows on, rather than along
          // the ground's normal: a plant on a slope grows towards the sky, not out of the hill sideways.
          var basis = new Basis(Vector3.Up, rng.Float() * Mathf.Tau).Scaled(new Vector3(scale, scale, scale));

          var cell = CellOf(at, edge);
          if (!cells.TryGetValue(cell, out var list))
          {
            list = new List<Transform3D>();
            cells[cell] = list;
          }

          list.Add(new Transform3D(basis, at));
        }
      }

      // Shuffled so that VisibleInstanceCount's prefix is a subset of the whole cell rather than of whichever
      // triangles the mesher emitted first. Without this, halving the count on a distant cell would empty one
      // side of it and leave the other at full density.
      foreach (var transforms in cells.Values)
      {
        for (var i = transforms.Count - 1; i > 0; i--)
        {
          var j = (int)(rng.Next() % (uint)(i + 1));
          (transforms[i], transforms[j]) = (transforms[j], transforms[i]);
        }
      }

      return cells;
    }

    /// <summary>
    /// Square metres of this surface that can carry grass, each weighted by how much grass it is.
    /// </summary>
    /// <remarks>
    /// Exactly the quantity <see cref="Scatter"/>'s main loop multiplies by the density, summed ahead of time
    /// so the cap can be turned into a density before any of it is spent. Another pass over the index buffer
    /// and nothing else - no allocation, no random numbers - against a loop that is already walking it.
    /// </remarks>
    private float GrassyArea(Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var total = 0.0f;

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        if ((normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        total += (vertices[ib] - a).Cross(vertices[ic] - a).Length() * 0.5f * grass;
      }

      return total;
    }

    /// <summary>Which world-aligned cell a point falls in, packed into one key.</summary>
    private static long CellOf(Vector3 at, float edge)
    {
      var x = Mathf.FloorToInt(at.X / edge);
      var z = Mathf.FloorToInt(at.Z / edge);

      return ((long)x << 32) ^ (uint)z;
    }

    /// <summary>
    /// Retunes every cell's instance count for where the camera is now.
    /// </summary>
    /// <remarks>
    /// Cheap enough to run unconditionally: a subtraction and a rounding per cell, with the assignment skipped
    /// unless the rounded count actually changed - which, at a metre or so of camera movement, it mostly does
    /// not.
    /// </remarks>
    public override void _Process(double delta)
    {
      var eye = Eye();

      Drain(eye);

      if (_patches.Count == 0 || !eye.HasValue)
      {
        return;
      }

      foreach (var patches in _patches.Values)
      {
        foreach (var patch in patches)
        {
          Retune(patch, eye.Value);
        }
      }
    }

    /// <summary>Sets one cell's instance count for a camera at <paramref name="eye"/>.</summary>
    private void Retune(Patch patch, Vector3 eye)
    {
      // Distance from a point to a box: how far outside each face the eye is, or zero on the axes where it is
      // between them. Zero altogether when the camera is standing in the cell.
      var outside = (patch.Min - eye).Max(eye - patch.Max).Max(Vector3.Zero);

      var visible = Mathf.RoundToInt(patch.Total * FractionAt(outside.Length()));

      if (visible == patch.Visible)
      {
        return;
      }

      patch.Visible = visible;
      patch.Multi.VisibleInstanceCount = visible;

      // Hidden rather than merely emptied. A multimesh with zero visible instances still costs its place in the
      // culling pass, and beyond the fade radius that is most of what the terrain holds.
      patch.Node.Visible = visible > 0;
    }

    /// <summary>
    /// Where the camera is, or null if there is not one yet.
    /// </summary>
    /// <remarks>
    /// Null is a real state and not only a test's: this node is built by <c>game.gd</c> before the scene has
    /// finished coming up, so the first chunks can be installed with no camera to measure against. It leaves a
    /// cell at its full count rather than at none, because the two failures are not comparable - a frame or two
    /// of grass drawn too densely is a frame or two of grass, and grass that defaults to hidden and never hears
    /// otherwise is a feature that silently does nothing.
    /// </remarks>
    private Vector3? Eye()
    {
      var camera = GetViewport()?.GetCamera3D();

      return camera == null ? null : camera.GlobalPosition;
    }

    /// <summary>What share of a cell's clumps to draw at this distance from it, 1 down to 0.</summary>
    /// <remarks>
    /// Squared rather than linear, so most of the saving is taken in the first few metres past
    /// <see cref="FullDensityMetres"/> where there is most ground to save it on, and the last clumps go out
    /// gradually instead of the far edge of the field stepping.
    /// </remarks>
    private float FractionAt(float distance)
    {
      if (distance <= FullDensityMetres)
      {
        return 1.0f;
      }

      if (distance >= FadeOutMetres)
      {
        return 0.0f;
      }

      var span = Mathf.Max(FadeOutMetres - FullDensityMetres, 0.001f);
      var t = 1.0f - (distance - FullDensityMetres) / span;

      return t * t;
    }

    public void Remove(ChunkKey key)
    {
      _pending.Remove(key);

      if (!_patches.Remove(key, out var patches))
      {
        return;
      }

      foreach (var patch in patches)
      {
        if (IsInstanceValid(patch.Node))
        {
          patch.Node.QueueFree();
        }
      }
    }

    public void Clear()
    {
      _pending.Clear();

      foreach (var key in new List<ChunkKey>(_patches.Keys))
      {
        Remove(key);
      }
    }

    /// <summary>Loads the mesh and material once. False if the mesh could not be loaded.</summary>
    /// <remarks>
    /// A failure is remembered, not retried. The alternative is a file-not-found for every chunk that streams
    /// in, which is the log for the rest of the session.
    /// </remarks>
    private bool Load()
    {
      if (_loaded)
      {
        return _clump != null;
      }

      _loaded = true;

      _clump = ResourceLoader.Load<Godot.Mesh>(ClumpPath);
      if (_clump == null)
      {
        GD.PushError($"[grass] {ClumpPath} did not load; the ground will have no grass on it.");
        return false;
      }

      _material = ResourceLoader.Load<ShaderMaterial>(MaterialPath);
      if (_material == null)
      {
        GD.PushError($"[grass] {MaterialPath} did not load; the grass keeps the mesh's own material.");
      }

      return true;
    }

    /// <summary>
    /// The scatter's random source: xorshift32, inlined so a chunk's worth of rolls allocates nothing.
    /// </summary>
    /// <remarks>
    /// Not <see cref="RandomNumberGenerator"/>, and not <c>System.Random</c>. What is wanted here is that the
    /// same chunk scatters the same grass on every client and after every re-mesh, forever - and neither of
    /// those documents its sequence as stable across versions. Six lines that do is the cheaper guarantee.
    /// </remarks>
    private struct Rng
    {
      private uint _state;

      internal Rng(uint seed)
      {
        _state = seed == 0u ? 0x9E3779B9u : seed;
      }

      internal uint Next()
      {
        _state ^= _state << 13;
        _state ^= _state >> 17;
        _state ^= _state << 5;

        return _state;
      }

      /// <summary>A float in [0, 1). The top 24 bits, because xorshift's low ones are the weakest.</summary>
      internal float Float() => (Next() >> 8) * (1.0f / 16777216.0f);
    }

    /// <summary>Avalanches a seed, so that chunks whose keys differ in one bit do not scatter alike.</summary>
    private static uint Mix(uint x)
    {
      x ^= x >> 16;
      x *= 0x7FEB352Du;
      x ^= x >> 15;
      x *= 0x846CA68Bu;
      x ^= x >> 16;

      return x;
    }
  }
}
