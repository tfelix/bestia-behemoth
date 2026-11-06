using System;
using System.IO;
using System.Text.RegularExpressions;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The server rolls a tree's variant and the client draws the model at that index, so both sides have to
  /// agree on how many tree models there are.
  /// </summary>
  /// <remarks>
  /// Too few variants on the server leave models that no tree ever gets. Too many wrap around and make some
  /// models twice as common. Neither shows up as an error, so this reads the server's <c>prop-kinds.yml</c>.
  /// </remarks>
  public class TreeModelTest
  {
    /// <summary><c>StaticEntityKind.TREE</c>.</summary>
    private const int Tree = 0;

    [Fact]
    public void ServerRollsOneVariantPerTreeModel()
    {
      var kinds = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "prop-kinds.yml"));
      var treeRow = Regex.Match(kinds, @"- kind: TREE\n(?:\s+.*\n)*?\s+variants: (\d+)");

      Assert.True(treeRow.Success, "prop-kinds.yml has no TREE row with variants");
      Assert.Equal(PropAppearance.Of(Tree).Scenes.Length, int.Parse(treeRow.Groups[1].Value));
    }
  }
}
