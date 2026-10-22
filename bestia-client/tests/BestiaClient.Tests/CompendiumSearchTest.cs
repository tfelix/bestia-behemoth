using BestiaBehemothClient.Game.UI.Compendium;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class CompendiumSearchTest
  {
    [Fact]
    public void NumbersArePaddedToThreeDigits()
    {
      Assert.Equal("#003", CompendiumSearch.NumberLabel(3));
      Assert.Equal("#120", CompendiumSearch.NumberLabel(120));
    }

    [Fact]
    public void AnEmptyQueryShowsEverySpeciesOnTheAllTab()
    {
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: false, caught: false, CompendiumSearch.TabAll, "  "));
    }

    [Theory]
    [InlineData("3")]
    [InlineData("#003")]
    [InlineData("00")]
    public void AQueryMatchesTheNumber(string query)
    {
      Assert.True(CompendiumSearch.Matches(3, "Blob", seen: true, caught: false, CompendiumSearch.TabAll, query));
    }

    [Fact]
    public void ASeenSpeciesMatchesItsNameInAnyCase()
    {
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: true, caught: false, CompendiumSearch.TabAll, "bL"));
    }

    [Fact]
    public void AnUnseenSpeciesDoesNotGiveItsNameAway()
    {
      Assert.False(CompendiumSearch.Matches(1, "Blob", seen: false, caught: false, CompendiumSearch.TabAll, "blob"));
    }

    [Fact]
    public void TabsFilterBySeenAndCaught()
    {
      Assert.False(CompendiumSearch.Matches(1, "Blob", seen: false, caught: false, CompendiumSearch.TabSeen, ""));
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: true, caught: false, CompendiumSearch.TabSeen, ""));
      Assert.False(CompendiumSearch.Matches(1, "Blob", seen: true, caught: false, CompendiumSearch.TabCaught, ""));
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: true, caught: true, CompendiumSearch.TabCaught, ""));
    }
  }
}
