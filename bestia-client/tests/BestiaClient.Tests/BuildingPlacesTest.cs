using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The kit ghost's copy of the server's building rules. The numbers and kinds are checked against
  /// <c>building-places.golden.json</c>, which the server's <c>BuildingPlacesGoldenTest</c> writes.
  /// </summary>
  public class BuildingPlacesTest
  {
    private const int Forge = 24;
    private const int Furnace = 23;
    private const int Workbench = 22;
    private const int Campfire = 55;
    private const int Residence = 20;
    private const long Builder = 3;
    private const long Stranger = 4;

    private static readonly BuildingPlaces.Settlement Town = new(new Vector3(0, 0, 0), 150f);

    [Fact]
    public void MatchesTheServersRules()
    {
      using var golden = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "building-places.golden.json")));
      var root = golden.RootElement;

      Assert.Equal(root.GetProperty("anchorRangeMetres").GetDouble(), BuildingPlaces.AnchorRangeMetres);
      Assert.Equal(root.GetProperty("wildSpacingMetres").GetDouble(), BuildingPlaces.WildSpacingMetres);
      Assert.Equal(root.GetProperty("citySign").GetInt32(), BuildingPlaces.CitySign);
      Assert.Equal(OrdinalsOf(root, "wildKinds"), BuildingPlaces.WildKinds.OrderBy(k => k));
      Assert.Equal(OrdinalsOf(root, "townAnchorKinds"), BuildingPlaces.TownAnchorKinds.OrderBy(k => k));
      Assert.Equal(OrdinalsOf(root, "generatedBuildingKinds"), BuildingPlaces.GeneratedBuildingKinds.OrderBy(k => k));
    }

    [Fact]
    public void AWildStructureMayStandInTheOpen()
    {
      Assert.Equal("", Refusal(Campfire, At(5000), settlement: null));
    }

    [Fact]
    public void ATownStructureInTheOpenNeedsASettlement()
    {
      Assert.Equal("structure_needs_settlement", Refusal(Forge, At(5000), settlement: null));
    }

    [Fact]
    public void ATownStructureNearTheCentreMayGoUp()
    {
      Assert.Equal("", Refusal(Forge, At(25), Town));
    }

    [Fact]
    public void ATownStructureFarFromEverythingIsTooFar()
    {
      Assert.Equal("structure_too_far_from_town", Refusal(Forge, At(100), Town));
    }

    [Fact]
    public void AFinishedTownBuildingOrAGeneratedHouseAnchorsTheNextOne()
    {
      Assert.Equal("", Refusal(Forge, At(100), Town, new BuildingPlaces.Neighbour(Furnace, At(80), Stranger, true)));
      Assert.Equal("", Refusal(Forge, At(100), Town, new BuildingPlaces.Neighbour(Residence, At(80), 0, true)));
    }

    [Fact]
    public void AWorkbenchOrASiteAnchorsNothing()
    {
      Assert.Equal("structure_too_far_from_town",
        Refusal(Forge, At(100), Town, new BuildingPlaces.Neighbour(Workbench, At(80), Builder, true)));
      Assert.Equal("structure_too_far_from_town",
        Refusal(Forge, At(100), Town, new BuildingPlaces.Neighbour(Furnace, At(80), Builder, false)));
    }

    [Fact]
    public void InTheOpenAnotherMastersCampIsTooCloseAndYourOwnIsNot()
    {
      var strangersFire = new BuildingPlaces.Neighbour(Campfire, At(5008), Stranger, true);
      var ownFire = new BuildingPlaces.Neighbour(Campfire, At(5002), Builder, true);

      Assert.Equal("structure_too_close", Refusal(Campfire, At(5000), null, strangersFire));
      Assert.Equal("", Refusal(Campfire, At(5000), null, ownFire));
    }

    [Fact]
    public void AGeneratedTreeKeepsNoDistance()
    {
      Assert.Equal("", Refusal(Campfire, At(5000), null, new BuildingPlaces.Neighbour(0, At(5001), 0, true)));
    }

    [Fact]
    public void InATownThereIsNoGap()
    {
      Assert.Equal("", Refusal(Forge, At(10), Town, new BuildingPlaces.Neighbour(Furnace, At(12), Stranger, true)));
    }

    private static string Refusal(
      int kind, Vector3 tile, BuildingPlaces.Settlement? settlement, params BuildingPlaces.Neighbour[] neighbours)
    {
      return BuildingPlaces.RefusalAt(kind, tile, Builder, settlement, neighbours);
    }

    private static Vector3 At(float x)
    {
      return new Vector3(x, 0, 0);
    }

    private static IEnumerable<int> OrdinalsOf(JsonElement root, string property)
    {
      return root.GetProperty(property).EnumerateObject().Select(p => p.Value.GetInt32()).OrderBy(k => k);
    }
  }
}
