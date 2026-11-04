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
    public void AnEmptyQueryShowsEverySeenSpeciesOnTheAllTab()
    {
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: true, caught: false, CompendiumSearch.TabAll, "  "));
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

    [Theory]
    [InlineData("")]
    [InlineData("#001")]
    [InlineData("blob")]
    public void AnUnseenSpeciesIsNeverListed(string query)
    {
      Assert.False(CompendiumSearch.Matches(1, "Blob", seen: false, caught: false, CompendiumSearch.TabAll, query));
    }

    [Fact]
    public void TheCaughtTabShowsOnlyCaughtSpecies()
    {
      Assert.False(CompendiumSearch.Matches(1, "Blob", seen: true, caught: false, CompendiumSearch.TabCaught, ""));
      Assert.True(CompendiumSearch.Matches(1, "Blob", seen: true, caught: true, CompendiumSearch.TabCaught, ""));
    }
  }
}
