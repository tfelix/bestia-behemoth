using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// How much one footfall wears and one death bleeds, at the server's default settings.
  /// </summary>
  /// <remarks>
  /// Amounts are contract sections 4 and 5, typed in rather than computed: wear is an Int times a Double
  /// truncated toward zero (6 * 0.8 is 4.8, so 4), and the blood falloff is the one place a sqrt and a division
  /// meet, so a client that rounded either would be a level off on every footfall in the world.
  /// </remarks>
  public class GroundSpreadTest
  {
    private const int Dirt = 11;
    private const int Mud = 12;
    private const int Grass = 13;
    private const int DryGrass = 14;
    private const int BlightedGrass = 16;
    private const int Cobblestone = 20;

    private static readonly WearRules Wear = new(
      259_200, 32, stepWeight: 6, neighbourWeight: 2,
      new Dictionary<int, double>
      {
        [Grass] = 1.0, [DryGrass] = 0.8, [BlightedGrass] = 0.9, [Mud] = 0.6, [Dirt] = 0.4
      });

    private static readonly BloodRules Blood = new(432_000, 16, spillWeight: 220, spillRadiusTiles: 2);

    private static Dictionary<(long, long), int> Spread(int block, SurfaceBlockAt blockAt = null)
    {
      var amounts = new List<CellAmount>();
      GroundSpread.Wear(Wear, 10, 20, 0, blockAt ?? ((x, y, z) => block), amounts);

      var byCell = new Dictionary<(long, long), int>();
      foreach (var cell in amounts)
      {
        byCell[(cell.VoxelX, cell.VoxelY)] = cell.Amount;
      }

      return byCell;
    }

    [Theory]
    [InlineData(Grass, 6, 2)]
    [InlineData(DryGrass, 4, 1)]
    [InlineData(BlightedGrass, 5, 1)]
    [InlineData(Mud, 3, 1)]
    public void AStepWearsTheTileAndTheFourBesideIt(int block, int centre, int beside)
    {
      var amounts = Spread(block);

      Assert.Equal(5, amounts.Count);
      Assert.Equal(centre, amounts[(10, 20)]);
      Assert.Equal(beside, amounts[(11, 20)]);
      Assert.Equal(beside, amounts[(9, 20)]);
      Assert.Equal(beside, amounts[(10, 21)]);
      Assert.Equal(beside, amounts[(10, 19)]);
    }

    /// <summary>Two and a bit tenths of a level is nothing once truncated, so dirt smears onto nothing.</summary>
    [Fact]
    public void DirtWearsItsOwnTileAndSpreadsToNothing()
    {
      var amounts = Spread(Dirt);

      Assert.Single(amounts);
      Assert.Equal(2, amounts[(10, 20)]);
    }

    /// <summary>A trail beside a road must not smear onto the paving.</summary>
    [Fact]
    public void EachNeighbourIsJudgedOnItsOwnBlock()
    {
      var amounts = Spread(0, (x, y, z) => x == 11 ? Cobblestone : y == 21 ? Mud : Grass);

      Assert.Equal(4, amounts.Count);
      Assert.Equal(6, amounts[(10, 20)]);
      Assert.False(amounts.ContainsKey((11, 20)));
      Assert.Equal(1, amounts[(10, 21)]);
      Assert.Equal(2, amounts[(9, 20)]);
      Assert.Equal(2, amounts[(10, 19)]);
    }

    [Fact]
    public void GroundThatDoesNotWearSpreadsNothingEvenOntoGrassBesideIt()
    {
      var amounts = Spread(0, (x, y, z) => x == 10 && y == 20 ? Cobblestone : Grass);

      Assert.Empty(amounts);
    }

    [Fact]
    public void AnUnknownTileIsSkippedRatherThanGuessed()
    {
      var amounts = Spread(0, (x, y, z) => x == 9 ? -1 : Grass);

      Assert.Equal(4, amounts.Count);
      Assert.False(amounts.ContainsKey((9, 20)));
    }

    [Fact]
    public void TheBlockIsLookedUpNearTheHeightOfTheStep()
    {
      var asked = new List<int>();
      var amounts = new List<CellAmount>();

      GroundSpread.Wear(Wear, 10, 20, 37, (x, y, z) => { asked.Add(z); return Grass; }, amounts);

      Assert.All(asked, z => Assert.Equal(37, z));
    }

    [Fact]
    public void ADeathSoaksAPoolOfThirteenCells()
    {
      var amounts = new List<CellAmount>();

      GroundSpread.Blood(Blood, 100, -50, amounts);

      Assert.Equal(13, amounts.Count);
    }

    [Fact]
    public void ThePoolFallsFromTheMiddleToNothingJustPastItsEdge()
    {
      var amounts = new List<CellAmount>();

      GroundSpread.Blood(Blood, 100, -50, amounts);

      var byCell = new Dictionary<(long, long), int>();
      foreach (var cell in amounts)
      {
        byCell[(cell.VoxelX, cell.VoxelY)] = cell.Amount;
      }

      Assert.Equal(220, byCell[(100, -50)]);

      Assert.Equal(146, byCell[(101, -50)]);
      Assert.Equal(146, byCell[(99, -50)]);
      Assert.Equal(146, byCell[(100, -49)]);
      Assert.Equal(146, byCell[(100, -51)]);

      Assert.Equal(116, byCell[(101, -49)]);
      Assert.Equal(116, byCell[(99, -51)]);

      Assert.Equal(73, byCell[(102, -50)]);
      Assert.Equal(73, byCell[(100, -52)]);

      // Two across and one up is 2.24 tiles away, past a radius of 2.
      Assert.False(byCell.ContainsKey((102, -49)));
    }
  }
}
