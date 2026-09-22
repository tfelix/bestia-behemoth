using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The item catalogue against the translation table it names.
  /// </summary>
  /// <remarks>
  /// <c>ItemDetail</c> is the first thing in the client to display an item's <c>description_key</c>. Until it
  /// existed a missing row cost nothing, because nothing looked the key up; now the lookup echoes the key
  /// back and the player reads <c>IRON_SWORD_DESC</c> off their own tooltip.
  ///
  /// <para>
  /// Asserted against <c>items.csv</c> itself rather than a list of expected keys, for the reason
  /// <see cref="EnumNameTest"/> gives: a list here would be a second copy of the keys, free to drift with the
  /// first. <c>./gradlew syncItemDb</c> keeps both sides in step, so this fails when a <c>.tres</c> was
  /// hand-edited without it.
  /// </para>
  /// </remarks>
  public class ItemDescriptionTest
  {
    /// <summary>
    /// Every <c>name_key</c> and <c>description_key</c> a <c>.tres</c> names has a row in
    /// <c>items.csv</c>.
    /// </summary>
    /// <remarks>
    /// One-directional, like the <c>ERROR_*</c> check: <c>items.csv</c> also carries rows for items that
    /// exist only in <c>items.yml</c> so far, and those are not a fault.
    /// </remarks>
    [Fact]
    public void EveryItemKeyHasATranslationRow()
    {
      var rows = TranslationKeys();
      var keys = ItemKeys();
      var missing = keys.Where(key => !rows.Contains(key)).ToList();

      Assert.NotEmpty(keys);
      Assert.True(missing.Count == 0,
        "item keys with no items.csv row: " + string.Join(", ", missing));
    }

    /// <summary>
    /// No item description contains a <c>[</c>.
    /// </summary>
    /// <remarks>
    /// <c>ItemDetail.as_bbcode</c> hands these rows to a <c>RichTextLabel</c> unescaped, so a square
    /// bracket in one would be read as markup - at best swallowing the text after it, at worst naming a tag
    /// like <c>[img]</c>. Escaping every row instead would be the alternative; this keeps the descriptions
    /// plain prose, which is what they are.
    /// </remarks>
    [Fact]
    public void NoItemDescriptionIsMarkup()
    {
      var offenders = File.ReadAllLines(ItemsCsv())
        .Where(line => line.Contains('['))
        .ToList();

      Assert.True(offenders.Count == 0,
        "items.csv rows containing BBCode markup: " + string.Join(" | ", offenders));
    }

    /// <summary>
    /// The keys named by every item resource. Godot writes them as bare <c>key = "VALUE"</c> assignments.
    /// </summary>
    private static List<string> ItemKeys()
    {
      var db = Path.Combine(AppContext.BaseDirectory, "ItemDB");
      var assignment = new Regex("(?:name_key|description_key) = \"([^\"]*)\"");

      return Directory.GetFiles(db, "*.tres")
        .SelectMany(file => assignment.Matches(File.ReadAllText(file)))
        .Select(match => match.Groups[1].Value)
        .Where(key => key.Length > 0)
        .Distinct()
        .ToList();
    }

    /// <summary>
    /// The keys of the shipped item translation table. Godot's CSV format is <c>key,locale...</c> with the
    /// key first and unescaped (<c>items.csv.import</c> sets <c>unescape_keys=false</c>), so the key is
    /// everything up to the first comma.
    /// </summary>
    private static HashSet<string> TranslationKeys()
    {
      return File.ReadAllLines(ItemsCsv())
        .Select(line => line.Split(',', 2)[0])
        .ToHashSet();
    }

    private static string ItemsCsv() => Path.Combine(AppContext.BaseDirectory, "items.csv");
  }
}
