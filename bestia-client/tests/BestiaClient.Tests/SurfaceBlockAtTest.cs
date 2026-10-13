using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The block a footfall lands on, read from the chunks this client holds.
  /// </summary>
  /// <remarks>
  /// Footprints and wear are gated on the raw block id, so the question is whether this finds the block the
  /// server's <c>SurfaceBlockLookup</c> would: the highest one in the column, which under water is the water.
  /// </remarks>
  public class SurfaceBlockAtTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;

    private static int BlockAt(IChunkSource source, long x, long y, int nearZ) =>
      SurfaceProbe.BlockAt(source, x, y, nearZ, Size, Height, ChunkWrap.None);

    private static FakeChunkSource Meadow()
    {
      var source = new FakeChunkSource();
      source.Put(TerrainFixtures.Flat(0, 0, 0, 40.3));

      return source;
    }

    [Fact]
    public void FindsTheCapOfTheGroundAtTheHeightOfTheStep()
    {
      Assert.Equal(TerrainFixtures.Grass, BlockAt(Meadow(), 5, 6, 40));
    }

    /// <summary>The server rounds a surface at 40.7 to 41, one above the voxel that holds the block.</summary>
    [Fact]
    public void FindsItFromOneAboveToo()
    {
      Assert.Equal(TerrainFixtures.Grass, BlockAt(Meadow(), 5, 6, 41));
    }

    /// <summary>The wear table is keyed by block id, and blocks that look alike - two grasses - wear differently.</summary>
    [Fact]
    public void ReadsTheIdTheChunkStores()
    {
      Assert.Equal(13, BlockAt(Meadow(), 0, 0, 40));
      Assert.Equal(4, BlockAt(Meadow(), 0, 0, 30));
    }

    [Fact]
    public void ReportsNothingWhereTheChunkIsNotHeld()
    {
      Assert.Equal(-1, BlockAt(Meadow(), 40, 6, 40));
    }

    [Fact]
    public void ReportsNothingWhenAllOfTheSearchIsAir()
    {
      Assert.Equal(-1, BlockAt(Meadow(), 5, 6, 60));
    }

    /// <summary>The search window reaches a slab that has not arrived; what is in it cannot be said.</summary>
    [Fact]
    public void ReportsNothingWhenTheSearchReachesASlabThatIsNotHeld()
    {
      Assert.Equal(-1, BlockAt(Meadow(), 5, 6, 254));
    }

    [Fact]
    public void ReadsTheColumnLeftOfTheOrigin()
    {
      var source = new FakeChunkSource();
      source.Put(TerrainFixtures.Flat(-1, -1, 0, 40.3));

      Assert.Equal(TerrainFixtures.Grass, BlockAt(source, -1, -1, 40));
    }

    /// <summary>A lake bed is water to the server, so a wader leaves no print and wears nothing.</summary>
    [Fact]
    public void GroundUnderWaterReadsAsWater()
    {
      var source = new FakeChunkSource();
      source.Put(TerrainFixtures.Submerged(0, 0, 0, 30, 38.0));

      Assert.Equal(TerrainFixtures.Water, BlockAt(source, 5, 6, 36));
    }
  }
}
