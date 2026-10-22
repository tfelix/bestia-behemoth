using Godot;

namespace BestiaBehemothClient.Game.UI.Compendium
{
  /// <summary>Hands <see cref="CompendiumSearch"/> to GDScript, which cannot call a static C# method.</summary>
  [GlobalClass]
  public partial class CompendiumSearcher : RefCounted
  {
    public string NumberLabel(int number)
    {
      return CompendiumSearch.NumberLabel(number);
    }

    public bool Matches(int number, string name, bool seen, bool caught, int tab, string query)
    {
      return CompendiumSearch.Matches(number, name, seen, caught, tab, query);
    }
  }
}
