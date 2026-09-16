using System;
using System.IO;
using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The prop-action name bridge, and the GDScript table on the far side of it.
  /// </summary>
  /// <remarks>
  /// <see cref="PropAppearance.PropAction"/> reaches <c>PropPicker.action</c> as a name, and
  /// <c>DefaultAction.from_prop_name</c> turns it back into the cursor to draw. A name that side does not
  /// know falls back to the plain pointer - the right failure at runtime, and an invisible one in review: an
  /// action added here and not there would simply stop showing its tool, with nothing thrown and nothing
  /// logged. Reading the GDScript table itself makes that drift a build failure instead, on the same
  /// argument <see cref="EnumNameTest"/> makes for reading <c>general.csv</c>.
  /// </remarks>
  public class PropActionTest
  {
    [Fact]
    public void EveryActionHasAName()
    {
      foreach (var action in Enum.GetValues<PropAppearance.PropAction>())
      {
        Assert.False(string.IsNullOrEmpty(PropAppearance.ActionName(action)));
      }
    }

    /// <summary>
    /// Indexing the name table by the enum is only sound while the values are contiguous from zero.
    /// </summary>
    [Fact]
    public void ActionValuesAreContiguousFromZero()
    {
      var values = Enum.GetValues<PropAppearance.PropAction>().Select(action => (int)action);

      Assert.Equal(Enumerable.Range(0, Enum.GetValues<PropAppearance.PropAction>().Length), values);
    }

    /// <summary>
    /// Every name this side emits is one <c>default_action.gd</c> maps, <c>none</c> excepted: that table
    /// leaves it out deliberately, because it is the fallback for everything it does not list.
    /// </summary>
    [Fact]
    public void GdscriptMapsEveryActionName()
    {
      var table = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "default_action.gd"));

      foreach (var action in Enum.GetValues<PropAppearance.PropAction>())
      {
        if (action == PropAppearance.PropAction.None)
        {
          continue;
        }

        Assert.Contains($"&\"{PropAppearance.ActionName(action)}\":", table);
      }
    }
  }
}
