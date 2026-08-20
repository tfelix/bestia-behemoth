using System;
using System.Linq;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The probe's contract with the mesher.
  /// </summary>
  /// <remarks>
  /// One assertion here carries the whole feature: <see cref="AgreesWithTheMesherOnASlope"/>. The probe exists so
  /// that a model's feet meet the ground the player can see, and the only way that can be true is if it returns
  /// the same height <see cref="SurfaceNets"/> put the triangle at. Everything else - the exactness on flat
  /// ground, the water mask, the local search - is a property of the reconstruction that the agreement test would
  /// not localise if it broke.
  /// </remarks>
  public class SurfaceProbeTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;

    private static double Probe(IChunkSource source, double voxelX, double voxelY, double nearZ) =>
      SurfaceProbe.SurfaceAt(source, TerrainFixtures.Appearance(), voxelX, voxelY, nearZ, Size, Height, ChunkWrap.None);

    /// <summary>
    /// Surrounds a chunk with its neighbours, so a probe near the middle reads genuine terrain on all sides.
    /// </summary>
    private static FakeChunkSource Surrounded(Func<int, int, VoxelChunk> build, int radius = 1)
    {
      var source = new FakeChunkSource();

      for (var chunkY = -radius; chunkY <= radius; chunkY++)
      {
        for (var chunkX = -radius; chunkX <= radius; chunkX++)
        {
          source.Put(build(chunkX, chunkY));
        }
      }

      return source;
    }

    [Fact]
    public void ReconstructsTheSubVoxelSurfaceOfFlatGround()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));

      // Not 40.3: a byte cannot hold that fraction, and the claim worth testing is that the probe loses nothing
      // further - the same distinction TerrainFixtures.Quantised exists to make for the mesher.
      Assert.Equal(TerrainFixtures.Quantised(40.3), Probe(source, 16, 16, 40), 6);
    }

    /// <summary>The probe is what an entity's rendered height is corrected by, so state the size of that.</summary>
    [Fact]
    public void FindsTheGroundTheServersRoundedHeightMisses()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));

      // What ChunkCoords.standingZ would have sent for a surface at 40.3, and what the model would stand at.
      const double Sent = 40.0;

      var offset = Probe(source, 16, 16, Sent) - Sent;

      Assert.InRange(offset, 0.29, 0.31);
    }

    /// <summary>
    /// The probe and the mesher put the surface in the same place on sloped ground.
    /// </summary>
    /// <remarks>
    /// A linear ramp rather than <c>Rolling</c>, and the reason is what makes the test meaningful rather than
    /// merely green. A mesh vertex is the mean of a cell's edge crossings and sits somewhere inside that cell,
    /// while the probe interpolates between lattice lines - so on arbitrary terrain the two answer slightly
    /// different questions and any tolerance loose enough to pass would be too loose to catch a real error. On a
    /// plane both are exact: every edge crossing lies on it, so the mean of them does too, and the quantisation
    /// the two share cancels instead of accumulating. So the tolerance below is not "close enough to look right",
    /// it is the width of a <c>float</c> at this elevation - the two agree to the last bit the mesh can store,
    /// and anything that made the probe read the field even slightly differently would blow straight past it.
    /// </remarks>
    [Fact]
    public void AgreesWithTheMesherOnASlope()
    {
      var source = Surrounded((x, y) => Ramp(x, y, 40.0, 0.25));

      var mesh = SurfaceNets.Build(source, new ChunkKey(0, 0, 0), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None);
      Assert.NotNull(mesh?.Terrain);

      // Away from the chunk's own edges, where the neighbourhood is genuine rather than an extended apron.
      var interior = mesh.Terrain.Vertices
        .Where(v => v.X > 2 && v.X < Size - 2 && v.Z > 2 && v.Z < Size - 2)
        .ToList();

      Assert.NotEmpty(interior);

      var worst = 0.0;

      foreach (var vertex in interior)
      {
        // Godot's y is the server's z, and its z is the server's y - the swap SurfaceNets applies on the way out.
        var probed = Probe(source, vertex.X, vertex.Z, vertex.Y);

        Assert.False(double.IsNaN(probed), $"no surface found under the mesh vertex at {vertex}");

        worst = Math.Max(worst, Math.Abs(probed - vertex.Y));
      }

      Assert.True(worst < 1e-4, $"probe and mesh disagree by up to {worst:E2} voxels");
    }

    /// <summary>
    /// Wading does not put an entity on top of the water.
    /// </summary>
    /// <remarks>
    /// The mesher masks occupancy per surface and the probe has to mask it the same way, or the first shallow
    /// lake anyone walks into lifts every model in it onto the sheet.
    /// </remarks>
    [Fact]
    public void StandsOnTheLakeBedRatherThanTheWater()
    {
      var source = Surrounded((x, y) => Flooded(x, y, 40.3, 44));

      // Probed from the waterline, which is where a wading entity's server height would put it.
      Assert.Equal(TerrainFixtures.Quantised(40.3), Probe(source, 16, 16, 43), 6);
    }

    /// <summary>
    /// The search is anchored on the caller's hint, so a cave floor wins over the hillside above it.
    /// </summary>
    /// <remarks>
    /// This is the whole justification for taking a hint instead of scanning the column from the top. Walking
    /// down from the roof of the world would put anything standing inside a cave, under a bridge deck or in a
    /// mine on the surface far above its head.
    /// </remarks>
    [Fact]
    public void FindsTheSurfaceNearestTheHintRatherThanTheHighestOne()
    {
      var source = Surrounded((x, y) => TerrainFixtures.WithCave(x, y, 40.3, 20, 30));

      // The cave floor: cell 19 is full and cell 20 is air, so the corner at lattice 20 sits exactly on the
      // isolevel and the surface is drawn at 20.
      Assert.Equal(20.0, Probe(source, 16, 16, 22), 6);

      // Same column, same chunk, hinted from outside: the hillside.
      Assert.Equal(TerrainFixtures.Quantised(40.3), Probe(source, 16, 16, 40), 6);
    }

    [Fact]
    public void GivesUpWhenNothingIsWithinReachOfTheHint()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));

      // A flying or falling entity. Better to leave it where the server put it than to drag it to the ground.
      Assert.True(double.IsNaN(Probe(source, 16, 16, 40 + SurfaceProbe.SearchVoxels + 2)));
    }

    /// <summary>
    /// Terrain that has not been streamed yet is <c>NaN</c>, not a guess.
    /// </summary>
    /// <remarks>
    /// Half an answer would be worse than none: the caller holds its last offset when the probe declines, which
    /// is invisible, whereas a height computed from an apron of extended edge columns would move models around at
    /// the edge of the streamed disc every time a chunk arrived.
    /// </remarks>
    [Fact]
    public void DeclinesWhereTheNeighbouringChunkIsNotHeld()
    {
      var source = new FakeChunkSource();
      source.Put(TerrainFixtures.Flat(0, 0, 0, 40.3));

      // Lattice 0 averages in the cells at x = -1, which belong to a chunk nobody has sent.
      Assert.True(double.IsNaN(Probe(source, 0, 16, 40)));

      // Well inside the one chunk that is held, the same probe answers.
      Assert.False(double.IsNaN(Probe(source, 16, 16, 40)));
    }

    /// <summary>
    /// Ground rising linearly along x, sampled at column centres the way the generator samples it.
    /// </summary>
    /// <remarks>
    /// The <c>+ 0.5</c> is not a detail. A cell spans <c>[i, i+1]</c> and <c>WorldConfig.columnCenter</c> takes
    /// its height at the middle, so a fixture that used the cell index directly would shift the whole surface
    /// half a voxel sideways and make the probe look like it had an off-by-half where the fixture did.
    /// </remarks>
    private static VoxelChunk Ramp(int chunkX, int chunkY, double baseElevation, double slope)
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      for (var localY = 0; localY < Size; localY++)
      {
        for (var localX = 0; localX < Size; localX++)
        {
          var surface = baseElevation + slope * (chunkX * Size + localX + 0.5);
          var top = (int)Math.Floor(surface);
          var offset = (localY * Size + localX) * Height;

          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = z > top - 3 ? TerrainFixtures.Grass : TerrainFixtures.Granite;
            occupancy[offset + z] = 255;
          }

          blocks[offset + top] = TerrainFixtures.Grass;
          occupancy[offset + top] = (byte)Math.Max(1, Math.Round((surface - top) * 255.0));
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, Size, Height, blocks, occupancy);
    }

    /// <summary>Flat ground under standing water, for the mask test.</summary>
    private static VoxelChunk Flooded(int chunkX, int chunkY, double surface, int waterLevel)
    {
      var chunk = TerrainFixtures.Flat(chunkX, chunkY, 0, surface);
      var top = (int)Math.Floor(surface);

      for (var column = 0; column < Size * Size; column++)
      {
        var offset = column * Height;

        for (var z = top + 1; z < waterLevel; z++)
        {
          chunk.Blocks[offset + z] = TerrainFixtures.Water;
          chunk.Occupancy[offset + z] = 255;
        }

        chunk.Blocks[offset + waterLevel] = TerrainFixtures.Water;
        chunk.Occupancy[offset + waterLevel] = (byte)Math.Round(TerrainFixtures.WaterFraction * 255.0);
      }

      return chunk;
    }
  }
}
