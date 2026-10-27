using System;
using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The designed shoreline: where it runs, and that every chunk that draws a piece of it draws the same piece.
  /// </summary>
  public class ShoreFieldTest
  {
    private const int Size = TerrainFixtures.Size;
    private const double Waterline = 40.5;

    /// <summary>
    /// A shelf rising a centimetre per metre toward +x, its waterline wandering across the border between chunk 0
    /// and chunk 1 - the coast the shaping exists for, cut where two chunks have to agree about it.
    /// </summary>
    private static double Ground(int x, int y) => Waterline + 0.01 * (x - Size + 0.5 + 5.0 * Math.Sin(y / 4.0));

    private static FakeChunkSource Coast()
    {
      var source = new FakeChunkSource();
      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 2; chunkX++)
        {
          source.Put(TerrainFixtures.Shore(chunkX, chunkY, Ground, Waterline));
        }
      }

      return source;
    }

    private static ChunkSurface Terrain(FakeChunkSource source, int chunkX) =>
      SurfaceNets.Build(source, new ChunkKey(chunkX, 0, 0), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None)
        .Terrain;

    [Fact]
    public void Two_chunks_shape_every_vertex_they_share_to_the_same_height()
    {
      var source = Coast();
      var left = Terrain(source, 0);
      var right = Terrain(source, 1);

      // A vertex both chunks emit has the same horizontal position in both, computed from the same voxels, so
      // its x and z are the key; its height is what the shaping could have made disagree.
      var heights = new Dictionary<(float, float), float>();
      foreach (var vertex in left.Vertices)
      {
        heights.TryAdd((vertex.X, vertex.Z), vertex.Y);
      }

      var shared = 0;
      foreach (var vertex in right.Vertices)
      {
        if (heights.TryGetValue((vertex.X, vertex.Z), out var height))
        {
          Assert.Equal(height, vertex.Y);
          shared++;
        }
      }

      Assert.True(shared > Size, $"only {shared} vertices are shared across the border; the test proves nothing");
    }

    [Fact]
    public void The_ground_crosses_the_water_on_the_shoreline_at_a_clear_slope()
    {
      var source = Coast();
      var shore = ShoreField.Build(source, new ChunkKey(0, 0, 0), ChunkWrap.None, TerrainFixtures.Appearance());
      Assert.NotNull(shore);

      // A metre either side of the curve, the shaped ground stands clear of the water by more than the
      // occupancy's few millimetres - which is the whole of what was wrong with the waterline before.
      foreach (var vertex in Terrain(source, 0).Vertices.Where(v => v.Y > Waterline - 1.0f && v.Y < Waterline + 1.0f))
      {
        if (!shore.TryRead(vertex.X, vertex.Z, out var distance, out var level) || Math.Abs(distance) < 1.0f
            || Math.Abs(distance) > 1.4f)
        {
          continue;
        }

        // Within the occupancy's 1/255 step, which is how finely the fixture can write the waterline.
        Assert.Equal((float)Waterline, level, 2);
        Assert.True(Math.Abs(vertex.Y - level) > 0.08f,
          $"vertex at ({vertex.X}, {vertex.Z}), {distance:F2} from the shore, stands only {vertex.Y - level:F3} off the water");
        Assert.Equal(Math.Sign(distance), Math.Sign(vertex.Y - level));
      }
    }

    [Fact]
    public void Dry_land_far_from_any_water_has_no_shore()
    {
      var source = new FakeChunkSource();
      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(TerrainFixtures.Flat(chunkX, chunkY, 0, 60.3));
        }
      }

      Assert.Null(ShoreField.Build(source, new ChunkKey(0, 0, 0), ChunkWrap.None, TerrainFixtures.Appearance()));
    }
  }
}
