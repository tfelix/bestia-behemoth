using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Crafting;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The enum-to-GDScript name bridge, and the translation rows that depend on its spelling.
  /// </summary>
  /// <remarks>
  /// Pins down a failure that had no symptom. <c>chat.gd</c> builds an <c>ERROR_*</c> key out of
  /// <c>OperationError.CodeName</c> and returns quietly when <c>tr()</c> hands the key back, because most
  /// operation errors belong to a window that reports them itself. So when <c>CodeName</c> lost its
  /// underscores - <c>ChartNeedsBlank</c> lowercased to <c>chartneedsblank</c>, never
  /// <c>chart_needs_blank</c> - every refusal in the game took that quiet path and no error was logged, no
  /// text was shown, and nothing failed. The same silence swallowed <c>crafting.gd</c>'s result messages and
  /// <c>crafting_row.gd</c>'s effect titles.
  ///
  /// <para>
  /// Which is why the assertion below is against <c>general.csv</c> itself rather than a list of expected
  /// strings: a list here would be a second copy of the keys, free to drift with the first. Reading the file
  /// makes a renamed <c>OpError</c> value or a mistyped row a build failure instead.
  /// </para>
  /// </remarks>
  public class EnumNameTest
  {
    /// <summary>
    /// Multi-word members are where the old <c>ToString().ToLowerInvariant()</c> went wrong, single-word
    /// ones are where it looked fine - both spellings are contracts some GDScript file matches on.
    /// </summary>
    [Theory]
    [InlineData(global::Bnet.OpError.ChartNeedsBlank, "chart_needs_blank")]
    [InlineData(global::Bnet.OpError.BasicSkillChatLocked, "basic_skill_chat_locked")]
    [InlineData(global::Bnet.OpError.AiConfigBestiaNotOwned, "ai_config_bestia_not_owned")]
    [InlineData(global::Bnet.OpSuccess.CraftSucceeded, "craft_succeeded")]
    [InlineData(RecipeEffect.AddSlot, "add_slot")]
    [InlineData(RecipeEffect.Repair, "repair")]
    public void SplitsAtCaseBoundaries(Enum value, string expected)
    {
      Assert.Equal(expected, EnumName.Of(value));
    }

    /// <summary>
    /// protoc strips an enum's own name off its values, so <c>TRADE_STATUS_OPEN</c> reaches C# as
    /// <c>Open</c>. Reading the member name rather than the proto's original name is what keeps
    /// <c>trade.gd</c>'s <c>"open"</c> working, and this is the case that rules the alternative out.
    /// </summary>
    [Fact]
    public void KeepsProtocsStrippedEnumPrefixStripped()
    {
      Assert.Equal("open", EnumName.Of(global::Bnet.TradeStatus.Open));
      Assert.Equal("cancelled", EnumName.Of(global::Bnet.TradeStatus.Cancelled));
    }

    /// <summary>
    /// Every <c>ERROR_*</c> row has to name a real <see cref="global::Bnet.OpError"/> value, spelled the way
    /// <c>chat.gd</c> spells it: <c>"ERROR_%s" % CodeName.to_upper()</c>.
    /// </summary>
    /// <remarks>
    /// Deliberately one-directional. The reverse - every code has a row - is false by design: a code the
    /// equipment window or the crafting window words itself has no business in the chat table.
    /// </remarks>
    [Fact]
    public void EveryErrorRowNamesAKnownCode()
    {
      var known = Enum.GetValues<global::Bnet.OpError>()
        .Select(code => "ERROR_" + EnumName.Of(code).ToUpperInvariant())
        .ToHashSet();

      var rows = ErrorKeysInGeneralCsv();
      var unknown = rows.Where(key => !known.Contains(key)).ToList();

      Assert.NotEmpty(rows);
      Assert.True(unknown.Count == 0,
        "general.csv rows naming no OpError value: " + string.Join(", ", unknown));
    }

    /// <summary>
    /// The keys of every <c>ERROR_*</c> row in the shipped translation table. Godot's CSV format is
    /// <c>key,locale...</c> with the key first and unescaped (<c>general.csv.import</c> sets
    /// <c>unescape_keys=false</c>), so the key is everything up to the first comma.
    /// </summary>
    private static List<string> ErrorKeysInGeneralCsv()
    {
      var csv = Path.Combine(AppContext.BaseDirectory, "general.csv");

      return File.ReadAllLines(csv)
        .Select(line => line.Split(',', 2)[0])
        .Where(key => key.StartsWith("ERROR_", StringComparison.Ordinal))
        .ToList();
    }
  }
}
