using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client's copy of the building pieces against the server's: both rasterize every piece in all four turns and
  /// compare against <c>structure-cells.golden.json</c>, which <c>StructurePieceCatalogueTest</c> writes.
  /// </summary>
  public class StructurePiecesTest
  {
    private static readonly Dictionary<string, int> Kinds = new()
    {
      ["STRUCTURE_WALL"] = StructurePieces.Wall,
      ["STRUCTURE_FLOOR"] = StructurePieces.Floor,
      ["STRUCTURE_STAIRS"] = StructurePieces.Stairs
    };

    [Fact]
    public void RasterizesEveryPieceAsTheServerDoes()
    {
      var json = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "structure-cells.golden.json"));
      var golden = JsonSerializer.Deserialize<Dictionary<string, long[][][]>>(json);

      Assert.Equal(Kinds.Keys.OrderBy(k => k), golden.Keys.OrderBy(k => k));
      foreach (var (name, turns) in golden)
      {
        for (var turn = 0; turn < 4; turn++)
        {
          var expected = turns[turn].Select(row => string.Join(",", row));
          var actual = StructurePieces.Rasterize(Kinds[name], 0, 0, 0, turn)
            .OrderBy(c => c.X).ThenBy(c => c.Y).ThenBy(c => c.Z).ThenBy(c => c.Edges)
            .Select(c => $"{c.X},{c.Y},{c.Z},{c.Fill},{c.Edges}");

          Assert.True(expected.SequenceEqual(actual), $"{name} turned {turn} times differs from the server");
        }
      }
    }

    [Fact]
    public void AYawRoundsToTheNearestQuarterTurn()
    {
      Assert.Equal(0, StructurePieces.QuarterTurnsOf(0.1f));
      Assert.Equal(1, StructurePieces.QuarterTurnsOf((float)(Math.PI / 2)));
      Assert.Equal(3, StructurePieces.QuarterTurnsOf((float)(-Math.PI / 2)));
    }
  }
}
