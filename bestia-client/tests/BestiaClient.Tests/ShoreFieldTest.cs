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
      var checkedAny = false;
      foreach (var vertex in Terrain(source, 0).Vertices.Where(v => v.Y > Waterline - 1.0f && v.Y < Waterline + 1.0f))
      {
        if (!shore.TryRead(vertex.X, vertex.Z, out var distance, out var level) || Math.Abs(distance) < 1.0f
            || Math.Abs(distance) > 1.4f)
        {
          continue;
        }

        // Within the occupancy's 1/255 step, which is how finely the fixture can write the waterline.
        Assert.Equal((float)Waterline, level, 2);
        Assert.True(distance > 0 ? vertex.Y - level > 0.06f : level - vertex.Y > 0.3f,
          $"vertex at ({vertex.X}, {vertex.Z}), {distance:F2} from the shore, stands only " +
          $"{vertex.Y - level:F3} off the water");
        checkedAny = true;
      }

      Assert.True(checkedAny, "no vertex lies a metre from the shore; the test proves nothing");
    }

    /// <summary>
    /// The water sheet ends in a staircase on the voxel grid, and is carried on under the bank: wherever an edge
    /// of the sheet shows, the coast is drawn as that staircase rather than as the curve.
    /// </summary>
    [Fact]
    public void The_bank_stands_over_every_edge_of_the_water_sheet()
    {
      var source = Coast();
      var worst = float.MaxValue;
      var edges = 0;

      for (var chunkX = 0; chunkX <= 1; chunkX++)
      {
        var mesh = SurfaceNets.Build(
          source, new ChunkKey(chunkX, 0, 0), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None);
        Assert.NotNull(mesh.Water);

        // Clear of the chunk's own borders, where the sheet is cut rather than ended.
        foreach (var vertex in OpenEdgeVertices(mesh.Water).Where(v =>
                   v.X > chunkX * Size + 1.5f && v.X < (chunkX + 1) * Size - 1.5f && v.Z > 1.5f && v.Z < Size - 1.5f))
        {
          var ground = HeightUnder(mesh.Terrain, vertex.X, vertex.Z);
          Assert.False(float.IsNaN(ground), $"no ground drawn under the water's edge at {vertex}");
          worst = Math.Min(worst, ground - vertex.Y);
          edges++;
        }
      }

      Assert.True(edges > Size, $"only {edges} edge vertices of the sheet were found; the test proves nothing");
      Assert.True(worst > 0.02f, $"the water's edge stands {-worst:F3} above the bank somewhere");
    }

    /// <summary>
    /// Carrying the edge out must not fold the sheet over open water: the water is drawn translucent from both
    /// sides, so a fold draws it twice and shows as a darker patch.
    /// </summary>
    /// <remarks>
    /// Measured against the same sheet left where the mesher put it, because that already covers a few spots
    /// twice: the short side faces along its rim lie almost flat at the waterline. Carrying stretches those
    /// under the bank, where the ground hides them.
    /// </remarks>
    [Fact]
    public void Carrying_the_sheet_to_the_shore_covers_no_more_open_water_twice()
    {
      var source = Coast();
      var appearance = TerrainFixtures.Appearance();
      var water = appearance.MaskOf(BlockAppearance.SurfaceKind.Water);
      int carried = 0, uncarried = 0, open = 0;

      for (var chunkX = 0; chunkX <= 1; chunkX++)
      {
        var key = new ChunkKey(chunkX, 0, 0);
        var mesh = SurfaceNets.Build(source, key, appearance, 1.0f, ChunkWrap.None);
        var left = SurfaceNets.Build(
          TerrainPatch.Gather(source, key, ChunkWrap.None, appearance), water, appearance, 1.0f,
          BlockAppearance.SurfaceKind.Water);

        carried += OpenWaterCoveredTwice(mesh.Water, mesh.Terrain, chunkX, out var samples);
        uncarried += OpenWaterCoveredTwice(left, mesh.Terrain, chunkX, out _);
        open += samples;
      }

      Assert.True(open > Size * Size, $"only {open} samples of open water; the test proves nothing");
      Assert.True(carried <= uncarried + open / 2000,
        $"{carried} of {open} samples of open water are covered twice, against {uncarried} uncarried");
    }

    /// <summary>
    /// Samples a fifth of a voxel apart over a chunk where water shows over the ground, counting those covered by
    /// more than one roughly level triangle of the sheet.
    /// </summary>
    private static int OpenWaterCoveredTwice(ChunkSurface sheet, ChunkSurface ground, int chunkX, out int open)
    {
      // Each roughly level triangle filed under every column its bounds touch, so a sample tests a handful.
      var level = new Dictionary<(int, int), List<int>>();
      for (var i = 0; i < sheet.Indices.Length; i += 3)
      {
        var a = sheet.Vertices[sheet.Indices[i]];
        var b = sheet.Vertices[sheet.Indices[i + 1]];
        var c = sheet.Vertices[sheet.Indices[i + 2]];
        var normal = (b - a).Cross(c - a);
        if (normal.Length() < 1e-6f || Math.Abs(normal.Normalized().Y) < 0.3f)
        {
          continue;
        }

        for (var x = (int)Math.Floor(Math.Min(a.X, Math.Min(b.X, c.X))); x <= Math.Max(a.X, Math.Max(b.X, c.X)); x++)
        {
          for (var z = (int)Math.Floor(Math.Min(a.Z, Math.Min(b.Z, c.Z))); z <= Math.Max(a.Z, Math.Max(b.Z, c.Z)); z++)
          {
            if (!level.TryGetValue((x, z), out var list))
            {
              level[(x, z)] = list = new List<int>();
            }

            list.Add(i);
          }
        }
      }

      var twice = 0;
      open = 0;
      for (var x = chunkX * Size + 1.1f; x < (chunkX + 1) * Size - 1; x += 0.2f)
      {
        for (var z = 1.1f; z < Size - 1; z += 0.2f)
        {
          if (!level.TryGetValue(((int)Math.Floor(x), (int)Math.Floor(z)), out var near))
          {
            continue;
          }

          var covers = 0;
          var top = float.NaN;
          foreach (var i in near)
          {
            var height = HeightOn(sheet, i, x, z);
            if (!float.IsNaN(height))
            {
              covers++;
              top = float.IsNaN(top) ? height : Math.Max(top, height);
            }
          }

          if (covers == 0 || HeightUnder(ground, x, z) > top + 0.005f)
          {
            continue;
          }

          open++;
          if (covers > 1)
          {
            twice++;
          }
        }
      }

      return twice;
    }

    [Fact]
    public void An_entity_stands_on_the_beach_that_is_drawn()
    {
      var source = Coast();
      var shores = new ShoreFieldCache();
      var appearance = TerrainFixtures.Appearance();
      var worst = 0.0;
      var shaped = 0;

      foreach (var vertex in Terrain(source, 0).Vertices.Where(v =>
                 v.X > 2 && v.X < Size - 2 && v.Z > 2 && v.Z < Size - 2 && Math.Abs(v.Y - Waterline) < 1.0f))
      {
        var probed = SurfaceProbe.ShapedSurfaceAt(
          source, appearance, shores, vertex.X, vertex.Z, vertex.Y, Size, TerrainFixtures.Height, ChunkWrap.None);
        var unshaped = SurfaceProbe.SurfaceAt(
          source, appearance, vertex.X, vertex.Z, vertex.Y, Size, TerrainFixtures.Height, ChunkWrap.None);

        Assert.False(double.IsNaN(probed), $"no surface found under the mesh vertex at {vertex}");
        worst = Math.Max(worst, Math.Abs(probed - vertex.Y));
        if (Math.Abs(probed - unshaped) > 0.01)
        {
          shaped++;
        }
      }

      Assert.True(shaped > Size, $"only {shaped} vertices were moved by the shaping; the test proves nothing");
      Assert.True(worst < 1e-4, $"probe and mesh disagree by up to {worst:E2} voxels on the beach");
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

    /// <summary>Vertices on an edge only one triangle of the surface uses: where the sheet ends.</summary>
    private static IEnumerable<Vector3> OpenEdgeVertices(ChunkSurface surface)
    {
      var uses = new Dictionary<(int, int), int>();
      for (var i = 0; i < surface.Indices.Length; i += 3)
      {
        for (var e = 0; e < 3; e++)
        {
          var a = surface.Indices[i + e];
          var b = surface.Indices[i + (e + 1) % 3];
          var edge = (Math.Min(a, b), Math.Max(a, b));
          uses[edge] = uses.GetValueOrDefault(edge) + 1;
        }
      }

      return uses.Where(entry => entry.Value == 1)
        .SelectMany(entry => new[] { entry.Key.Item1, entry.Key.Item2 })
        .Distinct()
        .Select(index => surface.Vertices[index]);
    }

    /// <summary>The height of the highest triangle of <paramref name="surface"/> over a point, or NaN.</summary>
    private static float HeightUnder(ChunkSurface surface, float x, float z)
    {
      var best = float.NaN;
      for (var i = 0; i < surface.Indices.Length; i += 3)
      {
        var height = HeightOn(surface, i, x, z);
        if (float.IsNaN(best) || height > best)
        {
          best = height;
        }
      }

      return best;
    }

    /// <summary>The height of the triangle starting at index <paramref name="i"/> over a point, or NaN.</summary>
    private static float HeightOn(ChunkSurface surface, int i, float x, float z)
    {
      var a = surface.Vertices[surface.Indices[i]];
      var b = surface.Vertices[surface.Indices[i + 1]];
      var c = surface.Vertices[surface.Indices[i + 2]];

      var area = (b.X - a.X) * (c.Z - a.Z) - (c.X - a.X) * (b.Z - a.Z);
      if (Math.Abs(area) < 1e-9f)
      {
        return float.NaN;
      }

      var u = ((b.X - x) * (c.Z - z) - (c.X - x) * (b.Z - z)) / area;
      var v = ((c.X - x) * (a.Z - z) - (a.X - x) * (c.Z - z)) / area;
      var w = 1.0f - u - v;
      const float Slack = -1e-5f;
      return u < Slack || v < Slack || w < Slack ? float.NaN : u * a.Y + v * b.Y + w * c.Y;
    }
  }
}
