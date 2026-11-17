using BestiaBehemothClient.Game.UI.Compendium;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class DropLookupTest
  {
    private const int Jelly = 1;
    private const int Apple = 2;
    private const int Log = 3;

    private static readonly SpeciesLoot Blob = new(10, new[] { Apple, Jelly }, new[] { 1000, 7000 });
    private static readonly SpeciesLoot Rootfather = new(20, new[] { Log, Jelly }, new[] { 10000, 7000 });
    private static readonly SpeciesLoot Wolf = new(30, new[] { Jelly }, new[] { 500 });

    [Fact]
    public void ASpeciesListsItsDropsLikeliestFirst()
    {
      Assert.Equal(
        new[] { new Drop(Jelly, 7000), new Drop(Apple, 1000) },
        DropLookup.DropsOf(Blob));
    }

    [Fact]
    public void ASpeciesWithoutLootDropsNothing()
    {
      Assert.Empty(DropLookup.DropsOf(new SpeciesLoot(40, new int[0], new int[0])));
    }

    [Fact]
    public void AnItemListsTheSpeciesThatDropItLikeliestFirstAndEqualChancesByNumber()
    {
      Assert.Equal(
        new[] { new Drop(10, 7000), new Drop(20, 7000), new Drop(30, 500) },
        DropLookup.DroppedBy(new[] { Wolf, Rootfather, Blob }, Jelly));
    }

    [Fact]
    public void AnItemNoSpeciesDropsHasNoSource()
    {
      Assert.Empty(DropLookup.DroppedBy(new[] { Blob, Wolf }, Log));
    }

    [Theory]
    [InlineData(10000, "100%")]
    [InlineData(8000, "80%")]
    [InlineData(250, "2.5%")]
    [InlineData(1, "0.01%")]
    public void AChanceReadsAsAPercentWithoutTrailingZeros(int chance, string label)
    {
      Assert.Equal(label, DropLookup.ChanceLabel(chance));
    }
  }
}
