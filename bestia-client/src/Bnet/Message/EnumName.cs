using System;
using System.Text;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Turns a C# enum member into the lowercase snake_case name the GDScript side matches on. GDScript
  /// cannot see a C# enum's members, so a message wrapper hands it a string instead - which is what keeps
  /// a new denial reason, trade status or recipe effect from being re-declared over there as a bare
  /// ordinal.
  /// </summary>
  /// <remarks>
  /// The obvious spelling, <c>value.ToString().ToLowerInvariant()</c>, is wrong and was wrong at four call
  /// sites: <see cref="Enum.ToString()"/> returns the member name, protoc generates that in PascalCase from
  /// the proto's SCREAMING_SNAKE_CASE, and lowercasing it welds the words together. <c>CHART_NEEDS_BLANK</c>
  /// arrived on the GDScript side as <c>chartneedsblank</c>, so the <c>ERROR_CHART_NEEDS_BLANK</c> row in
  /// <c>general.csv</c> was never found and every operation error was dropped in silence. Splitting at the
  /// case boundaries is what puts the underscores back.
  ///
  /// Deliberately not the protobuf descriptor's <c>OriginalName</c>, which would give the proto name
  /// verbatim: two of the five enums bridged this way (<c>RecipeEffect</c>, <c>DialogArgKind</c>) are
  /// hand-written C# and have no descriptor at all, and for <c>TradeStatus</c> - whose values carry a
  /// <c>TRADE_STATUS_</c> prefix that protoc strips - it would hand GDScript <c>trade_status_open</c> where
  /// <c>open</c> is wanted. Reading the member name keeps one rule for all five.
  ///
  /// Reversing protoc's transform is exact for letter-only names, and only those. A value with a digit in
  /// it (<c>LEVEL_2_LOCKED</c> becomes <c>Level2Locked</c>) cannot be reversed unambiguously - there is no
  /// telling <c>LEVEL_2</c> from <c>LEVEL2</c> - and none exists today. <c>EnumNameTest</c> cross-checks
  /// every <c>ERROR_*</c> row in <c>general.csv</c> against this method, so a code that ever breaks the
  /// assumption fails the build instead of going quiet again.
  /// </remarks>
  public static class EnumName
  {
    /// <summary>
    /// <c>OpError.ChartNeedsBlank</c> to <c>chart_needs_blank</c>, <c>RecipeEffect.AddSlot</c> to
    /// <c>add_slot</c>, <c>TradeStatus.Open</c> to <c>open</c>.
    /// </summary>
    public static string Of(Enum value)
    {
      var name = value.ToString();
      var snake = new StringBuilder(name.Length + 8);

      for (var i = 0; i < name.Length; i++)
      {
        if (i > 0 && char.IsUpper(name[i]))
        {
          snake.Append('_');
        }

        snake.Append(char.ToLowerInvariant(name[i]));
      }

      return snake.ToString();
    }
  }
}
