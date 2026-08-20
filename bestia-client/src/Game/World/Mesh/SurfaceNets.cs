using System;
using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Turns a chunk's occupancy field into a mesh, reconstructing the sub-voxel surface the generator wrote.
  /// </summary>
  /// <remarks>
  /// Surface nets: one vertex per cell that straddles the surface, quads between the cells around each lattice
  /// edge that crosses it. Chosen over marching cubes for being a fifth of the code with no 256-case table, and
  /// over a heightfield mesher for working on caves, overhangs and bridge decks without a second code path.
  ///
  /// <para><b>Occupancy is a cell volume fraction, not a point sample.</b> That distinction is the whole reason
  /// this reads the terrain correctly. The scalar field is sampled at cell <i>corners</i>, and a corner's value is
  /// the average of the eight cells meeting at it. A surface written at 40.3 m gives cells
  /// <c>z39 = 1.0, z40 = 0.3, z41 = 0.0</c>, hence corner values <c>0.65</c> at lattice 40 and <c>0.15</c> at
  /// lattice 41, and the crossing at 0.5 lands on <c>40 + (0.65-0.5)/(0.65-0.15) = 40.3</c>. Exactly, for every
  /// value, because the averaging is linear and the interpolation inverts it. Treating occupancy as a density
  /// sampled at cell centres instead would misplace that surface and, worse, could never put it above
  /// <c>topCell + 0.5</c>.
  /// </para>
  ///
  /// <para>
  /// It also means nothing has to declare which way a fraction fills. A half-full cell with rock below it and a
  /// half-full cell with rock above it both give a crossing at the same place, with the normal pointing the other
  /// way - the neighbours decide, so a cave ceiling needs no convention that a hillside does not.
  /// </para>
  ///
  /// <para>
  /// <b>The cost of corner averaging is a resolution floor, and it is asymmetric.</b> The field is a two-cell-wide
  /// box blur of occupancy, so a corner only falls below the isolevel once more than half the material in some
  /// 2x2x2 voxel box is gone. That threshold is volumetric: sub-voxel occupancy does not lower it, because the blur
  /// only ever sees the total. And because the inside test is <c>&gt;= Iso</c>, the tie breaks toward solid - so a
  /// one-voxel-thick <i>void</i> collapses and a one-voxel-thick <i>solid</i> does not. A thin wall renders
  /// correctly, at its true thickness, because the air cell beside it has one corner at 0.0 and one at 0.5 and that
  /// straddles; the wall's own cells sit at 0.5 on both faces and emit nothing, but they do not need to.
  /// </para>
  ///
  /// <para>
  /// This used to be written the other way round - "a one-cell-thick feature collapses" - which is wrong in the
  /// direction that invites the worst fix, namely deleting thin geometry server-side to match a mesher that draws
  /// it perfectly well. What actually needs handling is the void: a single carved voxel emits nothing at all, and
  /// apparent bore runs about <c>2R - 1</c> for a carve of radius R, with nothing drawn below about R = 1.3. The
  /// server therefore enforces a minimum bore radius rather than asking this class to mesh a smaller one. See
  /// <c>CarveVisibilityTest</c>, which measures every number in this paragraph, and <c>CarveBrush.MIN_RADIUS</c>.
  /// </para>
  /// </remarks>
  public static class SurfaceNets
  {
    /// <summary>The level the surface is drawn at. Half full is the only value that reconstructs the generator's
    /// own surface elevation exactly.</summary>
    public const float Iso = 0.5f;

    /// <summary>Cube corner offsets, indexed by <c>i + 2j + 4k</c>.</summary>
    private static readonly int[] OffsetX = { 0, 1, 0, 1, 0, 1, 0, 1 };
    private static readonly int[] OffsetY = { 0, 0, 1, 1, 0, 0, 1, 1 };
    private static readonly int[] OffsetZ = { 0, 0, 0, 0, 1, 1, 1, 1 };

    /// <summary>The twelve cube edges as corner-index pairs: four along x, then y, then z.</summary>
    private static readonly int[] EdgeA = { 0, 2, 4, 6, 0, 1, 4, 5, 0, 1, 2, 3 };
    private static readonly int[] EdgeB = { 1, 3, 5, 7, 2, 3, 6, 7, 4, 5, 6, 7 };

    /// <summary>
    /// Meshes one surface of a patch, or returns <c>null</c> if that surface has no geometry in this chunk.
    /// </summary>
    /// <param name="patch">The gathered cells.</param>
    /// <param name="includeMask">0xFF for block ids belonging to this surface, 0 for the rest. Masking the
    /// occupancy rather than the block id is what makes water and terrain two runs of the same code.</param>
    /// <param name="appearance">Vertex colours.</param>
    /// <param name="voxelSize">Metres per voxel, from the world info.</param>
    public static ChunkSurface Build(
      TerrainPatch patch, byte[] includeMask, BlockAppearance appearance, float voxelSize)
    {
      var width = patch.Width;
      var depth = patch.Depth;

      var field = BuildField(patch, includeMask);
      if (field == null)
      {
        return null;
      }

      // -1 for a cell with no vertex. Indexed exactly like the cell arrays, so the quad pass can look up its four
      // corners-of-the-dual with no search.
      var vertexAt = RentVertexIndex(patch.CellCount);

      var vertices = new List<Vector3>(2048);
      var normals = new List<Vector3>(2048);
      var colours = new List<Color>(2048);

      // Four bytes per vertex each, because that is the shape ArrayMesh wants an eight-bit custom channel in.
      var slotWeights0 = new List<byte>(2048 * 4);
      var slotWeights1 = new List<byte>(2048 * 4);

      var corner = new float[8];

      // Vertices for cells [1, Width-2] on each horizontal axis and [1, Depth-2] vertically: this chunk's own
      // cells plus the one just below each low edge, which the quads on that edge are built from.
      for (var py = 1; py <= width - 2; py++)
      {
        for (var px = 1; px <= width - 2; px++)
        {
          var mask = patch.ActiveMask(px - TerrainPatch.ApronLow, py - TerrainPatch.ApronLow);

          for (var pz = 1; pz <= depth - 2; pz++)
          {
            if ((mask[pz >> 6] & (1UL << (pz & 63))) == 0)
            {
              continue;
            }

            var inside = 0;
            for (var c = 0; c < 8; c++)
            {
              var value = field[Lattice(width, depth, px + OffsetX[c], py + OffsetY[c], pz + OffsetZ[c])];
              corner[c] = value;

              if (value >= Iso)
              {
                inside++;
              }
            }

            if (inside == 0 || inside == 8)
            {
              continue;
            }

            EmitVertex(
              patch, field, corner, px, py, pz, includeMask, appearance, voxelSize,
              vertices, normals, colours, slotWeights0, slotWeights1);

            vertexAt[(py * width + px) * depth + pz] = vertices.Count - 1;
          }
        }
      }

      if (vertices.Count == 0)
      {
        return null;
      }

      var indices = BuildQuads(patch, field, vertexAt);

      if (indices.Count == 0)
      {
        return null;
      }

      return new ChunkSurface
      {
        Vertices = vertices.ToArray(),
        Normals = normals.ToArray(),
        Colours = colours.ToArray(),
        SlotWeights0 = slotWeights0.ToArray(),
        SlotWeights1 = slotWeights1.ToArray(),
        Indices = indices.ToArray()
      };
    }

    private static int Lattice(int width, int depth, int px, int py, int pz) => (py * width + px) * depth + pz;

    /// <summary>
    /// Averages the cell fractions into corner samples, over the whole patch.
    /// </summary>
    /// <remarks>
    /// Not restricted by the active mask, deliberately. Leaving unmarked corners at zero would read as empty
    /// space, and the boundary between "computed" and "left at zero" is itself a sign change - so a solid chunk
    /// would grow a spurious surface wherever the mask happened to stop. There is no value that avoids this,
    /// because the correct value for an unmarked corner is whatever uniform run it sits in, which is exactly what
    /// skipping it fails to find out. The mask does its work one pass later, in <see cref="Build"/>, where the
    /// per-vertex cost actually is.
    ///
    /// <para>
    /// So this pass runs over every corner and has to be cheap per corner. An eight-cell average is separable:
    /// summing pairs along z, then along y, then along x gives the same total in three passes of one add each
    /// instead of one pass of eight reads and eight mask lookups. Measured at about a fifth of the cost, which
    /// matters because this is the only part of meshing that scales with patch volume rather than with surface
    /// area.
    /// </para>
    ///
    /// <para>
    /// The first pass doubles as the emptiness test. A patch with no water in it produces no non-zero cell, and
    /// the water surface is then abandoned after one linear scan rather than after the whole reduction - which is
    /// the common case, since most chunks are dry.
    /// </para>
    /// </remarks>
    private static float[] BuildField(TerrainPatch patch, byte[] includeMask)
    {
      var width = patch.Width;
      var depth = patch.Depth;
      var cells = patch.CellCount;

      var accumulator = RentAccumulator(cells);

      // Masking here rather than in the reduction: the surface a block belongs to is a property of the block, and
      // asking about it once per cell instead of once per corner is eight times fewer lookups.
      var blocks = patch.Blocks;
      var occupancies = patch.Occupancies;
      var any = false;

      for (var i = 0; i < cells; i++)
      {
        var value = occupancies[i] & includeMask[blocks[i]];
        accumulator[i] = value;

        if (value != 0)
        {
          any = true;
        }
      }

      if (!any)
      {
        return null;
      }

      // Pair along z, in place. Descending, so the value at z-1 is still the original when it is read.
      for (var column = 0; column < width * width; column++)
      {
        var start = column * depth;

        for (var pz = depth - 1; pz >= 1; pz--)
        {
          accumulator[start + pz] += accumulator[start + pz - 1];
        }

        // No z-1 exists here, so this corner has no meaning. Zeroed rather than left half-summed.
        accumulator[start] = 0;
      }

      // Pair along y, same trick one axis up.
      for (var py = width - 1; py >= 1; py--)
      {
        for (var px = 0; px < width; px++)
        {
          var here = (py * width + px) * depth;
          var under = ((py - 1) * width + px) * depth;

          for (var pz = 0; pz < depth; pz++)
          {
            accumulator[here + pz] += accumulator[under + pz];
          }
        }
      }

      Array.Clear(accumulator, 0, width * depth);

      // Pair along x, into the field itself. A separate destination, so the order does not matter here.
      var field = RentField(cells);
      Array.Clear(field, 0, cells);

      const float scale = 1.0f / (8.0f * 255.0f);

      for (var py = 1; py < width; py++)
      {
        for (var px = 1; px < width; px++)
        {
          var here = (py * width + px) * depth;
          var beside = (py * width + px - 1) * depth;

          for (var pz = 1; pz < depth; pz++)
          {
            field[here + pz] = (accumulator[here + pz] + accumulator[beside + pz]) * scale;
          }
        }
      }

      return field;
    }

    /// <summary>
    /// Scratch buffers, one set per thread.
    /// </summary>
    /// <remarks>
    /// A patch is a few hundred kilobytes of working set and a view volume is a hundred and twenty-one of them,
    /// twice over for the two surfaces - about ninety megabytes of garbage per login if each one allocates. Each
    /// worker meshes one chunk at a time, so a thread-local buffer that only ever grows is enough; no pool, no
    /// locking, and no return path to forget.
    /// </remarks>
    [ThreadStatic] private static int[] _accumulator;

    [ThreadStatic] private static float[] _field;

    [ThreadStatic] private static int[] _vertexAt;

    /// <summary>
    /// One accumulator per slot, reused for every vertex.
    /// </summary>
    /// <remarks>
    /// Eight ints. Thread-local for the same reason as the buffers above rather than for its size - a
    /// <c>stackalloc</c> would do, but a <c>Span</c> cannot be captured by <c>AccumulateSurface</c>'s inner
    /// function, and hoisting that function's body inline to avoid it would repeat the bounds test seven times.
    /// </remarks>
    [ThreadStatic] private static int[] _slotWeight;

    private static int[] RentAccumulator(int cells)
    {
      if (_accumulator == null || _accumulator.Length < cells)
      {
        _accumulator = new int[cells];
      }

      return _accumulator;
    }

    private static float[] RentField(int cells)
    {
      if (_field == null || _field.Length < cells)
      {
        _field = new float[cells];
      }

      return _field;
    }

    private static int[] RentVertexIndex(int cells)
    {
      if (_vertexAt == null || _vertexAt.Length < cells)
      {
        _vertexAt = new int[cells];
      }

      Array.Fill(_vertexAt, -1, 0, cells);

      return _vertexAt;
    }

    private static int[] RentSlotWeights()
    {
      _slotWeight ??= new int[BlockAppearance.Slots];

      Array.Clear(_slotWeight);

      return _slotWeight;
    }

    private static void EmitVertex(
      TerrainPatch patch, float[] field, float[] corner,
      int px, int py, int pz,
      byte[] includeMask, BlockAppearance appearance, float voxelSize,
      List<Vector3> vertices, List<Vector3> normals, List<Color> colours,
      List<byte> slotWeights0, List<byte> slotWeights1)
    {
      float sumX = 0.0f, sumY = 0.0f, sumZ = 0.0f;
      var crossings = 0;

      for (var edge = 0; edge < 12; edge++)
      {
        var a = EdgeA[edge];
        var b = EdgeB[edge];

        var fa = corner[a];
        var fb = corner[b];

        if (fa >= Iso == fb >= Iso)
        {
          continue;
        }

        var t = (fa - Iso) / (fa - fb);

        sumX += OffsetX[a] + t * (OffsetX[b] - OffsetX[a]);
        sumY += OffsetY[a] + t * (OffsetY[b] - OffsetY[a]);
        sumZ += OffsetZ[a] + t * (OffsetZ[b] - OffsetZ[a]);
        crossings++;
      }

      var inverse = 1.0f / crossings;

      // Voxel-space position of this cell's low corner. Z is the vertical axis on the server, and index zero is
      // sea level rather than a world floor, so a negative chunk z is normal.
      var voxelX = (float)((long)patch.Key.X * patch.Size + px - TerrainPatch.ApronLow);
      var voxelY = (float)((long)patch.Key.Y * patch.Size + py - TerrainPatch.ApronLow);
      var voxelZ = (float)((long)patch.Key.Z * patch.Height + patch.LocalZOf(pz));

      // Server (x, y, z) is Godot (x, z, y) - the same swap PositionComponent does for entities.
      vertices.Add(new Vector3(
        (voxelX + sumX * inverse) * voxelSize,
        (voxelZ + sumZ * inverse) * voxelSize,
        (voxelY + sumY * inverse) * voxelSize));

      // Gradient of the field across the cube. It points into the material, because occupancy is high inside, so
      // the outward normal is its negation.
      var gradientX = corner[1] + corner[3] + corner[5] + corner[7]
                      - (corner[0] + corner[2] + corner[4] + corner[6]);
      var gradientY = corner[2] + corner[3] + corner[6] + corner[7]
                      - (corner[0] + corner[1] + corner[4] + corner[5]);
      var gradientZ = corner[4] + corner[5] + corner[6] + corner[7]
                      - (corner[0] + corner[1] + corner[2] + corner[3]);

      var normal = new Vector3(-gradientX, -gradientZ, -gradientY);
      normals.Add(normal.LengthSquared() > 1e-12f ? normal.Normalized() : Vector3.Up);

      var weights = RentSlotWeights();
      var dominant = AccumulateSurface(patch, px, py, pz, includeMask, appearance, weights);

      colours.Add(appearance.ColourOf(dominant));
      PackWeights(weights, appearance.SlotOf(dominant), slotWeights0, slotWeights1);
    }

    /// <summary>
    /// What this vertex is made of: how much of each texture slot, and which single material tints it.
    /// </summary>
    /// <remarks>
    /// The cell holding the vertex is usually the right answer - the surface voxel is the partially filled one and
    /// carries the surface cap. On a vertical face it can be air, with the material in the cell beside it, so the
    /// six face neighbours are considered too. Masked by the surface being built, so the water pass cannot pick up
    /// the riverbed.
    ///
    /// <para>
    /// <b>The two answers come from the same scan but not from the same rule, and that is deliberate.</b> Slot
    /// weights accumulate, because the point of them is that a grass-to-sand boundary is a gradient rather than a
    /// step. The tint takes the single fullest cell - the argmax this function has always returned - because
    /// averaging it would destroy the one thing the palette's colours are load bearing for. An ore voxel in a
    /// granite wall would come back as a sixth of a copper stain instead of copper, and
    /// <c>BlockAppearance</c>'s ore rows exist precisely so a player can tell the rim of a body from its middle.
    /// Grain blends; hue does not.
    /// </para>
    ///
    /// <para>
    /// <b>Every arithmetic step here is integer, and the seams depend on it.</b> The cell on a chunk's high edge
    /// is meshed by that chunk and by its neighbour both, and the two must agree to the bit or the boundary shows
    /// a hairline of the wrong material. They read the same seven cells - the aprons guarantee it - so identical
    /// integer operations over identical bytes give identical results. Floating-point accumulation would be
    /// deterministic too, but only for as long as nobody reorders the sum.
    /// </para>
    /// </remarks>
    private static byte AccumulateSurface(
      TerrainPatch patch, int px, int py, int pz, byte[] includeMask, BlockAppearance appearance, int[] weights)
    {
      var best = (byte)VoxelChunk.AirBlockId;
      var bestScore = -1;

      Consider(px, py, pz);
      Consider(px, py, pz - 1);
      Consider(px, py, pz + 1);
      Consider(px - 1, py, pz);
      Consider(px + 1, py, pz);
      Consider(px, py - 1, pz);
      Consider(px, py + 1, pz);

      return best;

      void Consider(int x, int y, int z)
      {
        if (x < 0 || y < 0 || z < 0 || x >= patch.Width || y >= patch.Width || z >= patch.Depth)
        {
          return;
        }

        var index = patch.PatchIndexOf(x, y, z);
        var block = patch.BlockAt(index);
        var score = patch.RawOccupancyAt(index) & includeMask[block];

        // Air and every material masked out of this surface score zero, so they weigh nothing. The argmax still
        // considers them, because a vertex whose whole neighbourhood scores zero has to name some material and
        // the one it used to name is the one it still does.
        weights[(int)appearance.SlotOf(block)] += score;

        if (score > bestScore)
        {
          bestScore = score;
          best = block;
        }
      }
    }

    /// <summary>
    /// Scales the accumulated weights to bytes summing to exactly 255 and appends them to the two channels.
    /// </summary>
    /// <remarks>
    /// Exactly 255 rather than approximately, so the shader can treat the weights as a partition and skip a
    /// normalising divide per pixel. Flooring every slot leaves a shortfall of at most <c>Slots - 1</c>, and it
    /// all goes to the dominant slot: it is the largest, so it absorbs seven parts in 255 invisibly, and picking
    /// it needs no tie-break - which matters more than the accuracy, because a rule that resolved ties by
    /// scanning could resolve them differently for the two chunks that share a seam vertex.
    ///
    /// <para>
    /// A vertex whose neighbourhood is entirely air or entirely some other surface's material gets no weight at
    /// all. That is not supposed to happen - a vertex exists because the field straddles the isolevel nearby -
    /// but the field is a 3x3x3 average and this scan is seven of those twenty-seven cells, so it is reachable.
    /// Grey is the answer, because grey is what an unknown material looks like everywhere else here.
    /// </para>
    /// </remarks>
    private static void PackWeights(
      int[] weights, BlockAppearance.SurfaceSlot dominant, List<byte> low, List<byte> high)
    {
      var total = 0;
      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        total += weights[slot];
      }

      if (total <= 0)
      {
        weights[(int)BlockAppearance.SurfaceSlot.Neutral] = 1;
        total = 1;
        dominant = BlockAppearance.SurfaceSlot.Neutral;
      }

      var assigned = 0;
      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        var share = weights[slot] * 255 / total;

        weights[slot] = share;
        assigned += share;
      }

      weights[(int)dominant] += 255 - assigned;

      for (var slot = 0; slot < 4; slot++)
      {
        low.Add((byte)weights[slot]);
        high.Add((byte)weights[slot + 4]);
      }
    }

    /// <summary>
    /// Stitches the vertices into quads, one per lattice edge that crosses the surface.
    /// </summary>
    /// <remarks>
    /// The loop bounds are the seam contract. This chunk owns the lattice edges at local <c>[0, Size)</c> on each
    /// horizontal axis, so its neighbour owns the ones at <c>Size</c> - which are that neighbour's own zero. Every
    /// shared face is drawn once, by exactly one of the two chunks.
    ///
    /// <para>
    /// Winding is emitted counter-clockwise as seen from the empty side, in the server's right-handed Z-up frame.
    /// Mapping to Godot swaps two axes, which is a reflection and flips orientation, and Godot treats clockwise as
    /// front-facing - the two cancel, so the index order carries over unchanged. If terrain ever renders as
    /// backfaces, this comment and a reversal here are the place to look.
    /// </para>
    /// </remarks>
    private static List<int> BuildQuads(TerrainPatch patch, float[] field, int[] vertexAt)
    {
      var width = patch.Width;
      var depth = patch.Depth;

      var indices = new List<int>(8192);

      var lo = TerrainPatch.ApronLow;
      var hiX = lo + patch.Size - 1;
      var hiZ = depth - 2;

      var quad = new int[4];

      for (var py = lo; py <= hiX; py++)
      {
        for (var px = lo; px <= hiX; px++)
        {
          for (var pz = lo; pz <= hiZ; pz++)
          {
            var here = field[Lattice(width, depth, px, py, pz)];
            var insideHere = here >= Iso;

            // An edge along x. Its four cells are at cell x = px, straddling y and z - and the counter-clockwise
            // order for a face whose normal is +x runs (y-,z-), (y+,z-), (y+,z+), (y-,z+).
            if (insideHere != field[Lattice(width, depth, px + 1, py, pz)] >= Iso)
            {
              quad[0] = vertexAt[((py - 1) * width + px) * depth + pz - 1];
              quad[1] = vertexAt[(py * width + px) * depth + pz - 1];
              quad[2] = vertexAt[(py * width + px) * depth + pz];
              quad[3] = vertexAt[((py - 1) * width + px) * depth + pz];

              Add(indices, quad, insideHere);
            }

            // Along y: normal +y, so the cyclic pair is (z, x).
            if (insideHere != field[Lattice(width, depth, px, py + 1, pz)] >= Iso)
            {
              quad[0] = vertexAt[(py * width + px - 1) * depth + pz - 1];
              quad[1] = vertexAt[(py * width + px - 1) * depth + pz];
              quad[2] = vertexAt[(py * width + px) * depth + pz];
              quad[3] = vertexAt[(py * width + px) * depth + pz - 1];

              Add(indices, quad, insideHere);
            }

            // Along z: normal +z, so the cyclic pair is (x, y).
            if (insideHere != field[Lattice(width, depth, px, py, pz + 1)] >= Iso)
            {
              quad[0] = vertexAt[((py - 1) * width + px - 1) * depth + pz];
              quad[1] = vertexAt[((py - 1) * width + px) * depth + pz];
              quad[2] = vertexAt[(py * width + px) * depth + pz];
              quad[3] = vertexAt[(py * width + px - 1) * depth + pz];

              Add(indices, quad, insideHere);
            }
          }
        }
      }

      return indices;
    }

    private static void Add(List<int> indices, int[] quad, bool forward)
    {
      // Surface nets guarantees all four cells around a crossing edge have a crossing of their own, so a missing
      // vertex would mean the field and the mask disagree. Dropping the quad keeps a bug from becoming a crash.
      if (quad[0] < 0 || quad[1] < 0 || quad[2] < 0 || quad[3] < 0)
      {
        return;
      }

      if (forward)
      {
        indices.Add(quad[0]);
        indices.Add(quad[1]);
        indices.Add(quad[2]);

        indices.Add(quad[0]);
        indices.Add(quad[2]);
        indices.Add(quad[3]);
      }
      else
      {
        indices.Add(quad[0]);
        indices.Add(quad[3]);
        indices.Add(quad[2]);

        indices.Add(quad[0]);
        indices.Add(quad[2]);
        indices.Add(quad[1]);
      }
    }

    /// <summary>Meshes every surface of a chunk.</summary>
    /// <remarks>
    /// One pass per <see cref="BlockAppearance.SurfaceKind"/> the palette actually uses. A pass costs one linear
    /// scan of the patch to find that it is empty, which is why the <see cref="BlockAppearance.Occupies"/> gate
    /// is worth having: on nearly every chunk in the world the lava pass would be that scan and nothing else.
    /// </remarks>
    public static ChunkMesh Build(
      IChunkSource source, ChunkKey key, BlockAppearance appearance, float voxelSize, ChunkWrap wrap)
    {
      var patch = TerrainPatch.Gather(source, key, wrap);
      if (patch == null)
      {
        return null;
      }

      var surfaces = new ChunkSurface[BlockAppearance.SurfaceKinds];
      var any = false;

      for (var kind = 0; kind < surfaces.Length; kind++)
      {
        if (!appearance.Occupies((BlockAppearance.SurfaceKind)kind))
        {
          continue;
        }

        surfaces[kind] = Build(patch, appearance.MaskOf((BlockAppearance.SurfaceKind)kind), appearance, voxelSize);
        any |= surfaces[kind] != null;
      }

      if (!any)
      {
        return null;
      }

      return new ChunkMesh
      {
        Key = key,
        Surfaces = surfaces,
        MissingNeighbours = patch.MissingNeighbours
      };
    }
  }
}
