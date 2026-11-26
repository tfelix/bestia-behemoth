using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// <c>item_enchantment_text.gd</c> names an enchantment by <c>"ITEM_ENCHANTMENT_%d" % EnchantmentId</c>. The ids
  /// live in the server's <c>item_enchantments.yml</c>, so a new one there without a row here would show its raw key.
  /// </summary>
  public class ItemEnchantmentNameTest
  {
    [Fact]
    public void EveryItemEnchantmentHasARow()
    {
      var catalog = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "item_enchantments.yml"));
      var ids = Regex.Matches(catalog, @"^\s*- id: (\d+)", RegexOptions.Multiline)
        .Select(match => match.Groups[1].Value)
        .ToList();

      var rows = File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "general.csv"))
        .Select(line => line.Split(',', 2)[0])
        .ToHashSet();
      var missing = ids.Select(id => "ITEM_ENCHANTMENT_" + id).Where(key => !rows.Contains(key)).ToList();

      Assert.NotEmpty(ids);
      Assert.True(missing.Count == 0, "general.csv lacks rows for: " + string.Join(", ", missing));
    }
  }
}
