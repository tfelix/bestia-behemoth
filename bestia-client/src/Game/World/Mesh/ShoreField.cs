using System;
using Godot;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Where the water's edge runs across a chunk, as a smooth curve rather than as the voxel grid has it.
  /// </summary>
  /// <remarks>
  /// <b>A shoreline is designed, not sampled.</b> On a coastal shelf rising a centimetre per metre the ground
  /// stays within millimetres of the water for metres at a time, and which of the two surfaces a pixel shows
  /// there is decided by the occupancy's four-millimetre steps: the waterline came out as a notched zig-zag on the
  /// voxel grid wherever the mesher put it. A level designer never lets a beach and the water lie that close.
  /// They draw the shore as a curve, take the beach clearly under the water along it, and run the water on under
  /// the land. This is that curve, which <see cref="SurfaceNets"/> then shapes the ground to.
  ///
  /// <para>
  /// Each column is wet - its topmost voxel is water - or dry. The curve is the zero line of the signed distance
  /// between the two kinds, positive on land, taken between column centres, blurred once, and read bilinearly at
  /// each vertex. Blurred so a staircase of wet columns comes out as a rounded shore and not a chamfered one.
  /// </para>
  ///
  /// <para>
  /// <b>Exactly the same answer from every chunk that asks.</b> A vertex on a chunk border is emitted by both
  /// chunks meshing it, and if the two shaped it differently the ground would crack along the seam. So every
  /// value is a function of the world's columns within a fixed reach of the column it describes, computed by
  /// one exact procedure - a brute-force search, never a distance transform propagated in from the edge of a
  /// window, which would hand different values to the same column depending on where the window was cut.
  /// </para>
  /// </remarks>
  public sealed class ShoreField
  {
    /// <summary>Columns searched for the nearest column of the other kind. Distances beyond are clamped.</summary>
    public const int Reach = 4;

    /// <summary>Columns past the chunk's own edge a vertex can be read at: the patch apron, plus one to interpolate.</summary>
    private const int VertexMargin = TerrainPatch.ApronLow + 1;

    /// <summary>Columns past the chunk's edge the blurred field covers.</summary>
    private const int Margin = VertexMargin + 1;

    /// <summary>Columns past the chunk's edge whose kind is read: the blur's ring, then the search.</summary>
    private const int ClassMargin = Margin + 1 + Reach;

    /// <summary>The distance to every offset in the search square, so the search takes no square roots.</summary>
    private static readonly float[] Offsets = BuildOffsets();

    private readonly int _originX;
    private readonly int _originY;
    private readonly int _width;
    private readonly float[] _distance;
    private readonly float[] _level;

    private ShoreField(int originX, int originY, int width, float[] distance, float[] level)
    {
      _originX = originX;
      _originY = originY;
      _width = width;
      _distance = distance;
      _level = level;
    }

    /// <summary>
    /// The field for one chunk, or <c>null</c> where no water is within reach of it, which is nearly everywhere.
    /// </summary>
    /// <remarks>
    /// A column's kind is read from its topmost voxel across this slab and the one above and below it, which
    /// covers the shore wherever it lies relative to the slab being meshed: a sea at zero is the floor of slab 0
    /// and the ceiling of slab -1, and both have to find it.
    /// </remarks>
    public static ShoreField Build(IChunkSource source, ChunkKey key, ChunkWrap wrap, BlockAppearance appearance)
    {
      var size = WorldLayout.ChunkSize;
      var classWidth = size + 2 * ClassMargin;
      var originX = key.X * size - ClassMargin;
      var originY = key.Y * size - ClassMargin;

      // NaN for a dry column, the water's surface height for a wet one. Columns in chunks not held are dry: the
      // patch reports those chunks as debts and the renderer meshes again when they land.
      var water = new float[classWidth * classWidth];
      var anyWet = false;

      var chunks = new VoxelChunk[3, 3, 3];
      var bands = new ChunkBands[3, 3, 3];
      var anyWater = false;
      for (var dy = -1; dy <= 1; dy++)
      {
        for (var dx = -1; dx <= 1; dx++)
        {
          for (var dz = -1; dz <= 1; dz++)
          {
            var neighbour = wrap.Normalise(new ChunkKey(key.X + dx, key.Y + dy, key.Z + dz));
            var chunk = source.Get(neighbour);
            var scan = source.BandsOf(neighbour);
            chunks[dx + 1, dy + 1, dz + 1] = chunk;
            bands[dx + 1, dy + 1, dz + 1] = scan;
            anyWater |= chunk != null && (scan == null || scan.HoldsWater);
          }
        }
      }

      // Nearly every chunk in the world: no water anywhere it could read, so no column search at all.
      if (!anyWater)
      {
        return null;
      }

      for (var cy = 0; cy < classWidth; cy++)
      {
        for (var cx = 0; cx < classWidth; cx++)
        {
          var level = SurfaceOf(chunks, bands, key, cx - ClassMargin, cy - ClassMargin, appearance);
          water[cy * classWidth + cx] = level;
          anyWet |= !float.IsNaN(level);
        }
      }

      if (!anyWet)
      {
        return null;
      }

      // Signed distance and the water level each column belongs to, over the blur's input ring.
      var rawWidth = size + 2 * (Margin + 1);
      var raw = new float[rawWidth * rawWidth];
      var rawLevel = new float[rawWidth * rawWidth];
      var shift = ClassMargin - (Margin + 1);

      // Wet columns counted over every rectangle from the window's corner, so whether a column's search square
      // holds both kinds is four reads. Nearly every column is all one kind for metres around and needs no search.
      var counts = new int[(classWidth + 1) * (classWidth + 1)];
      for (var cy = 0; cy < classWidth; cy++)
      {
        for (var cx = 0; cx < classWidth; cx++)
        {
          counts[(cy + 1) * (classWidth + 1) + cx + 1] = (float.IsNaN(water[cy * classWidth + cx]) ? 0 : 1)
            + counts[cy * (classWidth + 1) + cx + 1] + counts[(cy + 1) * (classWidth + 1) + cx]
            - counts[cy * (classWidth + 1) + cx];
        }
      }

      const int square = (2 * Reach + 1) * (2 * Reach + 1);

      for (var ry = 0; ry < rawWidth; ry++)
      {
        for (var rx = 0; rx < rawWidth; rx++)
        {
          var cx = rx + shift;
          var cy = ry + shift;
          var own = water[cy * classWidth + cx];
          var wet = !float.IsNaN(own);

          int Count(int x, int y) => counts[y * (classWidth + 1) + x];
          var wetAround = Count(cx + Reach + 1, cy + Reach + 1) - Count(cx - Reach, cy + Reach + 1)
                          - Count(cx + Reach + 1, cy - Reach) + Count(cx - Reach, cy - Reach);
          if (wetAround == 0 || wetAround == square)
          {
            raw[ry * rawWidth + rx] = wet ? -Reach : Reach;
            rawLevel[ry * rawWidth + rx] = own;
            continue;
          }

          var best = float.MaxValue;
          var bestLevel = own;
          for (var oy = -Reach; oy <= Reach; oy++)
          {
            for (var ox = -Reach; ox <= Reach; ox++)
            {
              var other = water[(cy + oy) * classWidth + cx + ox];
              if (wet == !float.IsNaN(other))
              {
                continue;
              }

              // Strictly nearer only, scanned in a fixed order, so a tie always goes the same way.
              var distance = Offsets[(oy + Reach) * (2 * Reach + 1) + ox + Reach];
              if (distance < best)
              {
                best = distance;
                if (!wet)
                {
                  bestLevel = other;
                }
              }
            }
          }

          // The edge runs between the two column centres, half a column from each.
          var reach = Mathf.Min(best, Reach + 0.5f) - 0.5f;
          raw[ry * rawWidth + rx] = wet ? -reach : reach;
          rawLevel[ry * rawWidth + rx] = bestLevel;
        }
      }

      // One binomial blur. The level is averaged over the neighbours that have one, so a river stepping down its
      // reach does not put a ledge in its banks.
      var width = size + 2 * Margin;
      var distanceField = new float[width * width];
      var levelField = new float[width * width];
      for (var y = 0; y < width; y++)
      {
        for (var x = 0; x < width; x++)
        {
          float sum = 0.0f, levelSum = 0.0f, levelWeight = 0.0f;
          for (var oy = -1; oy <= 1; oy++)
          {
            for (var ox = -1; ox <= 1; ox++)
            {
              var weight = (2 - Math.Abs(ox)) * (2 - Math.Abs(oy));
              var index = (y + 1 + oy) * rawWidth + x + 1 + ox;
              sum += weight * raw[index];
              if (!float.IsNaN(rawLevel[index]))
              {
                levelSum += weight * rawLevel[index];
                levelWeight += weight;
              }
            }
          }

          distanceField[y * width + x] = sum / 16.0f;
          levelField[y * width + x] = levelWeight > 0.0f ? levelSum / levelWeight : float.NaN;
        }
      }

      return new ShoreField(key.X * size - Margin, key.Y * size - Margin, width, distanceField, levelField);
    }

    /// <summary>
    /// The signed distance to the shore at a point, in voxels, positive on land, and the level of the water it
    /// belongs to. False where no water is within reach.
    /// </summary>
    /// <param name="voxelX">Server x in voxels, unwrapped into this chunk's frame.</param>
    /// <param name="voxelY">Server y in voxels, likewise.</param>
    public bool TryRead(float voxelX, float voxelY, out float distance, out float level)
    {
      // Values sit at column centres.
      var u = voxelX - _originX - 0.5f;
      var v = voxelY - _originY - 0.5f;
      var x0 = Mathf.Clamp((int)Mathf.Floor(u), 0, _width - 2);
      var y0 = Mathf.Clamp((int)Mathf.Floor(v), 0, _width - 2);
      var fx = Mathf.Clamp(u - x0, 0.0f, 1.0f);
      var fy = Mathf.Clamp(v - y0, 0.0f, 1.0f);

      var i = y0 * _width + x0;
      distance = Mathf.Lerp(
        Mathf.Lerp(_distance[i], _distance[i + 1], fx),
        Mathf.Lerp(_distance[i + _width], _distance[i + _width + 1], fx), fy);

      // The level of whichever corners have one: a corner with none has no water within reach.
      float levelSum = 0.0f, weightSum = 0.0f;
      Accumulate(_level[i], (1 - fx) * (1 - fy), ref levelSum, ref weightSum);
      Accumulate(_level[i + 1], fx * (1 - fy), ref levelSum, ref weightSum);
      Accumulate(_level[i + _width], (1 - fx) * fy, ref levelSum, ref weightSum);
      Accumulate(_level[i + _width + 1], fx * fy, ref levelSum, ref weightSum);

      level = weightSum > 0.0f ? levelSum / weightSum : float.NaN;
      return weightSum > 0.0f;
    }

    private static float[] BuildOffsets()
    {
      var side = 2 * Reach + 1;
      var offsets = new float[side * side];
      for (var oy = -Reach; oy <= Reach; oy++)
      {
        for (var ox = -Reach; ox <= Reach; ox++)
        {
          offsets[(oy + Reach) * side + ox + Reach] = Mathf.Sqrt(ox * ox + oy * oy);
        }
      }

      return offsets;
    }

    private static void Accumulate(float value, float weight, ref float sum, ref float weightSum)
    {
      if (float.IsNaN(value))
      {
        return;
      }

      sum += value * weight;
      weightSum += weight;
    }

    /// <summary>The water's surface height over one column, in voxels, or NaN if its topmost voxel is not water.</summary>
    private static float SurfaceOf(
      VoxelChunk[,,] chunks, ChunkBands[,,] bands, ChunkKey key, int localX, int localY, BlockAppearance appearance)
    {
      var size = WorldLayout.ChunkSize;
      var dx = localX < 0 ? -1 : localX >= size ? 1 : 0;
      var dy = localY < 0 ? -1 : localY >= size ? 1 : 0;
      var x = localX - dx * size;
      var y = localY - dy * size;

      for (var dz = 1; dz >= -1; dz--)
      {
        var chunk = chunks[dx + 1, dy + 1, dz + 1];
        if (chunk == null)
        {
          continue;
        }

        var offset = (y * chunk.Size + x) * chunk.Height;
        var scanBands = bands[dx + 1, dy + 1, dz + 1];
        var top = scanBands == null || !scanBands.IsUniform
          ? Math.Min(chunk.Height - 1, (scanBands?.MaxActiveZ ?? chunk.Height - 1) + 1)
          : chunk.Height - 1;

        // A uniform slab is all one block, so its top voxel answers for the whole column.
        if (scanBands != null && scanBands.IsUniform)
        {
          top = chunk.Blocks[offset + chunk.Height - 1] == VoxelChunk.AirBlockId ? -1 : chunk.Height - 1;
        }

        for (var z = top; z >= 0; z--)
        {
          var block = chunk.Blocks[offset + z];
          if (block == VoxelChunk.AirBlockId)
          {
            continue;
          }

          if (appearance.SurfaceOf(block) != BlockAppearance.SurfaceKind.Water)
          {
            return float.NaN;
          }

          return (key.Z + dz) * chunk.Height + z + chunk.Occupancy[offset + z] / 255.0f;
        }
      }

      return float.NaN;
    }
  }
}
