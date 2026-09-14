using System;
using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;
using Xunit;
using Xunit.Abstractions;

namespace BestiaBehemothClient.Tests
{
  public class ZzProbeTest
  {
    private const int Size = TerrainFixtures.Size;
    private readonly ITestOutputHelper _out;

    public ZzProbeTest(ITestOutputHelper output) => _out = output;

    private static FakeChunkSource Surrounded(Func<int, int, VoxelChunk> build, int radius = 2)
    {
      var source = new FakeChunkSource();
      for (var cy = -radius; cy <= radius; cy++)
      for (var cx = -radius; cx <= radius; cx++)
        source.Put(build(cx, cy));
      return source;
    }

    private static ChunkMesh Mesh(FakeChunkSource source) =>
      SurfaceNets.Build(source, new ChunkKey(0, 0, 0), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None);

    private static List<Vector3> Interior(ChunkSurface s) =>
      s.Vertices.Where(v => v.X > 2 && v.X < Size - 2 && v.Z > 2 && v.Z < Size - 2).ToList();

    [Theory]
    [InlineData(40)]
    [InlineData(39)]
    [InlineData(38)]
    [InlineData(37)]
    [InlineData(36)]
    [InlineData(34)]
    [InlineData(30)]
    [InlineData(20)]
    public void ShallowSeaBed(int bed)
    {
      const double waterline = 40.5;
      var source = Surrounded((x, y) => TerrainFixtures.Submerged(x, y, 0, bed, waterline));
      var mesh = Mesh(source);

      var water = mesh?.Water == null ? new List<Vector3>() : Interior(mesh.Water);
      var terrain = mesh?.Terrain == null ? new List<Vector3>() : Interior(mesh.Terrain);

      var loW = water.Count == 0 ? double.NaN : water.Min(v => v.Y);
      var hiW = water.Count == 0 ? double.NaN : water.Max(v => v.Y);
      var loT = terrain.Count == 0 ? double.NaN : terrain.Min(v => v.Y);
      var hiT = terrain.Count == 0 ? double.NaN : terrain.Max(v => v.Y);

      var atBed = water.Count(v => v.Y < waterline - 1.0);

      _out.WriteLine(
        $"bed={bed} depth={waterline - bed:F1}  water verts={water.Count} Y[{loW:F2},{hiW:F2}] atBed={atBed}" +
        $"   terrain verts={terrain.Count} Y[{loT:F2},{hiT:F2}]" +
        $"   waterTris={(mesh?.Water == null ? 0 : mesh.Water.TriangleCount)}" +
        $" terrainTris={(mesh?.Terrain == null ? 0 : mesh.Terrain.TriangleCount)}");
    }
  }
}
