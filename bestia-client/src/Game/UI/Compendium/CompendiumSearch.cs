using System;

namespace BestiaBehemothClient.Game.UI.Compendium
{
  /// <summary>Which Bestia Compendium entries a search and a tab show.</summary>
  public static class CompendiumSearch
  {
    public const int TabAll = 0;
    public const int TabCaught = 1;

    /// <summary><c>#003</c>: the number every entry shows.</summary>
    public static string NumberLabel(int number)
    {
      return $"#{number:000}";
    }

    /// <summary>
    /// A species never seen is not listed at all, on any tab: the book only holds what the master has met. A
    /// query matches the number, with or without its <c>#</c>, or the name.
    /// </summary>
    public static bool Matches(int number, string name, bool seen, bool caught, int tab, string query)
    {
      if (!seen) return false;
      if (tab == TabCaught && !caught) return false;

      var trimmed = query.Trim();
      if (trimmed.Length == 0) return true;

      if (NumberLabel(number).Contains(trimmed.TrimStart('#'), StringComparison.Ordinal)) return true;

      return name.Contains(trimmed, StringComparison.CurrentCultureIgnoreCase);
    }
  }
}
