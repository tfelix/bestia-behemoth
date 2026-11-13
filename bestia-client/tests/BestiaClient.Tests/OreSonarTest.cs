using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>Resource Sonar lights up the ore blocks the client already holds, within the skill's range.</summary>
  public class OreSonarTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;
    private const long OreX = 5;
    private const long OreY = 6;

    /// <summary>Granite under a gold vein three blocks deep, in one column; its top block is at z 40.</summary>
    private static FakeChunkSource Vein()
    {
      var source = new FakeChunkSource();
      source.Put(TerrainFixtures.Capped(0, 0, 40.3,
        (x, y) => x == OreX && y == OreY ? TerrainFixtures.GoldOre : TerrainFixtures.Granite));

      return source;
    }

    private static bool IsGold(int block) => block == TerrainFixtures.GoldOre;

    [Fact]
    public void FindsTheOreWithinRangeAndNothingElse()
    {
      var found = OreSonar.Around(Vein(), ChunkWrap.None, OreX, OreY, 45, 5, IsGold, Size, Height);

      Assert.Equal(new[] { new OreSonar.Find(OreX, OreY, 40, TerrainFixtures.GoldOre) }, found);
    }

    [Fact]
    public void ALongerRangeReachesDeeperIntoTheVeinNearestFirst()
    {
      var found = OreSonar.Around(Vein(), ChunkWrap.None, OreX, OreY, 45, 7, IsGold, Size, Height);

      Assert.Equal(new[] { 40, 39, 38 }, found.Select(f => f.Z));
    }

    [Fact]
    public void SeesNothingInChunksTheClientDoesNotHold()
    {
      var found = OreSonar.Around(Vein(), ChunkWrap.None, 100, 100, 40, 5, IsGold, Size, Height);

      Assert.Empty(found);
    }

    [Fact]
    public void ShowsOresAndGemsButNotRockSalt()
    {
      Assert.True(OreSonar.IsOreName("ORE_IRON_RICH"));
      Assert.True(OreSonar.IsOreName("GEM_DIAMOND_SMALL"));
      Assert.False(OreSonar.IsOreName("ROCK_SALT_RICH"));
    }
  }
}
