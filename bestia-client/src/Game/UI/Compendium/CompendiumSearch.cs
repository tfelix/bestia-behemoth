using System;

namespace BestiaBehemothClient.Game.UI.Compendium
{
  /// <summary>Which Bestia Compendium entries a search and a tab show.</summary>
  public static class CompendiumSearch
  {
    public const int TabAll = 0;
    public const int TabSeen = 1;
    public const int TabCaught = 2;

    /// <summary><c>#003</c>: the number every entry shows, seen or not.</summary>
    public static string NumberLabel(int number)
    {
      return $"#{number:000}";
    }

    /// <summary>
    /// A query matches the number, with or without its <c>#</c>, or the name. An unseen entry matches by number
    /// only: its name is still a secret, and a search must not give it away.
    /// </summary>
    public static bool Matches(int number, string name, bool seen, bool caught, int tab, string query)
    {
      if (tab == TabSeen && !seen) return false;
      if (tab == TabCaught && !caught) return false;

      var trimmed = query.Trim();
      if (trimmed.Length == 0) return true;

      if (NumberLabel(number).Contains(trimmed.TrimStart('#'), StringComparison.Ordinal)) return true;

      return seen && name.Contains(trimmed, StringComparison.CurrentCultureIgnoreCase);
    }
  }
}
