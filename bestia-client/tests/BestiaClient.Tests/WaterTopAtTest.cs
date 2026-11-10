using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>Where a fishing bobber floats: the water's top over the bed a click landed on.</summary>
  public class WaterTopAtTest
  {
    private static int WaterTopAt(IChunkSource source, int bedZ) =>
      SurfaceProbe.WaterTopAt(
        source, TerrainFixtures.Appearance(), 5, 6, bedZ, TerrainFixtures.Size, TerrainFixtures.Height, ChunkWrap.None);

    [Fact]
    public void ClimbsFromTheBedToTheFirstAirAboveTheWater()
    {
      var lake = new FakeChunkSource();
      lake.Put(TerrainFixtures.Submerged(0, 0, 0, 30, 38.0));

      Assert.Equal(38, WaterTopAt(lake, 29));
    }

    [Fact]
    public void DryGroundHasNoWaterTop()
    {
      var meadow = new FakeChunkSource();
      meadow.Put(TerrainFixtures.Flat(0, 0, 0, 40.3));

      Assert.Equal(-1, WaterTopAt(meadow, 40));
    }

    [Fact]
    public void AnUnheldChunkHasNoWaterTop()
    {
      Assert.Equal(-1, WaterTopAt(new FakeChunkSource(), 29));
    }
  }
}
