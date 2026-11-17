using System.Collections.Generic;
using System.Globalization;
using System.Linq;

namespace BestiaBehemothClient.Game.UI.Compendium
{
  /// <summary>One line of a drop list: a species or an item, and its chance out of <see cref="DropLookup.ChanceScale"/>.</summary>
  public readonly record struct Drop(int Id, int Chance);

  /// <summary>One species' loot as the bestia DB holds it: item i drops with chance i.</summary>
  public readonly record struct SpeciesLoot(int SpeciesId, int[] ItemIds, int[] Chances);

  /// <summary>Who drops what, read from the static bestia DB, so asking costs no server message.</summary>
  public static class DropLookup
  {
    /// <summary>The server's fixed point for a loot chance: this many is a sure drop.</summary>
    public const int ChanceScale = 10000;

    /// <summary>The items a species drops, likeliest first.</summary>
    public static List<Drop> DropsOf(SpeciesLoot species)
    {
      return Likeliest(species.ItemIds.Zip(species.Chances, (item, chance) => new Drop(item, chance)));
    }

    /// <summary>The species that drop <paramref name="itemId"/>, likeliest first.</summary>
    public static List<Drop> DroppedBy(IEnumerable<SpeciesLoot> species, int itemId)
    {
      return Likeliest(species
        .SelectMany(one => DropsOf(one)
          .Where(drop => drop.Id == itemId)
          .Select(drop => new Drop(one.SpeciesId, drop.Chance))));
    }

    /// <summary><c>80%</c>, <c>2.5%</c>: as many decimals as the chance has, and no more.</summary>
    public static string ChanceLabel(int chance)
    {
      var percent = chance * 100m / ChanceScale;
      return percent.ToString("0.##", CultureInfo.InvariantCulture) + "%";
    }

    private static List<Drop> Likeliest(IEnumerable<Drop> drops)
    {
      return drops.OrderByDescending(drop => drop.Chance).ThenBy(drop => drop.Id).ToList();
    }
  }
}
