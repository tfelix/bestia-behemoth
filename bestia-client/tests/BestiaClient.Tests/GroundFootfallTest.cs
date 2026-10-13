using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client half of the footprint heading and shape-seed contract.
  /// </summary>
  /// <remarks>
  /// Every value is typed in from <c>contract.md</c>, computed with 64-bit BigInt arithmetic outside both
  /// languages. A disagreement is silent - prints drawn twice because the client's copy never matches the
  /// server's - so the expectations must not come from this code's own output. <c>MarkingGroundTrampleTest</c>
  /// pins the same values against the Kotlin functions.
  /// </remarks>
  public class GroundFootfallTest
  {
    /// <summary>
    /// The eight steps of an eight-connected walk, and the cases where half-to-even rounding would be wrong.
    /// </summary>
    /// <remarks>
    /// (1,1) is 1.5 eighths and (-1,1) is 3.5: Kotlin rounds both up, .NET's <c>Math.Round</c> would take 2 and 4.
    /// </remarks>
    [Theory]
    [InlineData(1, 0, 0)]
    [InlineData(1, 1, 1)]
    [InlineData(0, 1, 2)]
    [InlineData(-1, 1, 3)]
    [InlineData(-1, 0, 4)]
    [InlineData(-1, -1, 5)]
    [InlineData(0, -1, 6)]
    [InlineData(1, -1, 7)]
    [InlineData(0, 0, 0)]
    [InlineData(0, 40, 2)]
    [InlineData(-17, -3, 4)]
    [InlineData(5, -2, 0)]
    public void OctantOf(long dx, long dy, int expected)
    {
      Assert.Equal(expected, GroundFootfall.OctantOf(dx, dy));
    }

    /// <summary>
    /// Negative coordinates, an id past 2^53, and values that overflow 64 bits mid-computation: the cases a
    /// checked multiply, an arithmetic shift or a signed modulo would each get wrong.
    /// </summary>
    [Theory]
    [InlineData(1L, 0L, 0L, 120)]
    [InlineData(77L, 3L, -3L, 217)]
    [InlineData(77L, -3L, 3L, 96)]
    [InlineData(5L, 100L, -20L, 58)]
    [InlineData(5L, -100L, 20L, 92)]
    [InlineData(1234567890123L, -1L, -1L, 51)]
    [InlineData(9000000000000000000L, 93287L, 36927L, 210)]
    [InlineData(-1L, 0L, 0L, 58)]
    [InlineData(42L, -2147483649L, 2147483648L, 227)]
    [InlineData(1L, 4L, 9L, 213)]
    [InlineData(2L, 4L, 9L, 138)]
    [InlineData(3L, 4L, 9L, 175)]
    public void SeedOf(long entityId, long voxelX, long voxelY, int expected)
    {
      Assert.Equal(expected, GroundFootfall.SeedOf(entityId, voxelX, voxelY));
    }
  }
}
