using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The bestia DB against the translation tables the Bestia Compendium reads, like
  /// <see cref="ItemDescriptionTest"/> does for items.
  /// </summary>
  public class BestiaDescriptionTest
  {
    [Fact]
    public void EverySpeciesKeyHasATranslationRow()
    {
      var rows = KeysOf("bestias.csv");
      var keys = Assignments("(?:name_key|description_key) = \"([^\"]*)\"");
      var missing = keys.Where(key => !rows.Contains(key)).ToList();

      Assert.NotEmpty(keys);
      Assert.True(missing.Count == 0, "bestia keys with no bestias.csv row: " + string.Join(", ", missing));
    }

    [Fact]
    public void EveryHabitatBiomeHasATranslationRow()
    {
      var rows = KeysOf("general.csv");
      var missing = Assignments("habitat = \"([^\"]*)\"")
        .SelectMany(habitat => habitat.Split(',', StringSplitOptions.RemoveEmptyEntries))
        .Select(biome => "BIOME_" + biome)
        .Where(key => !rows.Contains(key))
        .Distinct()
        .ToList();

      Assert.True(missing.Count == 0, "habitat biomes with no general.csv row: " + string.Join(", ", missing));
    }

    /// <summary>The value of every assignment matching <paramref name="pattern"/> across the bestia DB.</summary>
    private static List<string> Assignments(string pattern)
    {
      var db = Path.Combine(AppContext.BaseDirectory, "BestiaDB");
      var assignment = new Regex(pattern);

      return Directory.GetFiles(db, "*.tres")
        .SelectMany(file => assignment.Matches(File.ReadAllText(file)))
        .Select(match => match.Groups[1].Value)
        .Where(value => value.Length > 0)
        .Distinct()
        .ToList();
    }

    /// <summary>The keys of a translation table: everything up to the first comma, see <see cref="ItemDescriptionTest"/>.</summary>
    private static HashSet<string> KeysOf(string csv)
    {
      return File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, csv))
        .Select(line => line.Split(',', 2)[0])
        .ToHashSet();
    }
  }
}
