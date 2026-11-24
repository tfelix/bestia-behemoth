using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Where a kit may go down: the client's copy of the server's <c>BuildingPlaces</c>, <c>TownBuildingRule</c> and
  /// <c>WildSpacingRule</c>, so a kit ghost shows a refusal before the click.
  /// </summary>
  /// <remarks>
  /// The server still decides. Both copies are checked against <c>building-places.golden.json</c>, which
  /// <c>BuildingPlacesGoldenTest</c> writes. Distances are across the ground, in tiles of one metre.
  /// </remarks>
  public static class BuildingPlaces
  {
    public const float AnchorRangeMetres = 30f;
    public const float WildSpacingMetres = 10f;
    public const int CitySign = 66;

    /// <summary>Small camp and gathering structures, which may stand anywhere.</summary>
    public static readonly HashSet<int> WildKinds = new() { 22, 55, 57, 58, 59, 71, 90 };

    /// <summary>Finished player buildings a new town building may stand near.</summary>
    public static readonly HashSet<int> TownAnchorKinds = new() { 13, 23, 24, 52, 53, 54, 67, 75, 76, 77, 78, 86, 87, 88, 92 };

    /// <summary>What the world generator builds in its towns. A generated market is drawn as an auction house.</summary>
    public static readonly HashSet<int> GeneratedBuildingKinds = new() { 13, 14, 15, 16, 17, 18, 19, 20, 21 };

    /// <summary>Something standing near the spot: a static entity, or a construction site.</summary>
    /// <param name="OwnerMasterId">The master who built it, or 0 for anything the world generated.</param>
    public readonly record struct Neighbour(int Kind, Vector3 Tile, long OwnerMasterId, bool IsFinished);

    /// <summary>The settlement the builder stands in, as their <c>Place</c> describes it.</summary>
    public readonly record struct Settlement(Vector3 Centre, float Radius);

    /// <summary>
    /// The <c>OpError</c> name that refuses <paramref name="kind"/> at <paramref name="tile"/>, or "" where it may go.
    /// In the server's order: the town rule first, then the gap to other masters' structures.
    /// </summary>
    public static string RefusalAt(
      int kind, Vector3 tile, long builderMasterId, Settlement? settlement, IEnumerable<Neighbour> neighbours)
    {
      var near = new List<Neighbour>(neighbours);
      var isInSettlement = settlement is { } s && Distance(s.Centre, tile) <= s.Radius;

      if (NeedsSettlement(kind))
      {
        if (!isInSettlement)
        {
          return "structure_needs_settlement";
        }

        if (Distance(settlement.Value.Centre, tile) > AnchorRangeMetres && !near.Exists(n => AnchorsTown(n, tile)))
        {
          return "structure_too_far_from_town";
        }
      }

      if (!isInSettlement && near.Exists(n => IsOtherMasters(n, builderMasterId, tile)))
      {
        return "structure_too_close";
      }

      return "";
    }

    public static bool NeedsSettlement(int kind)
    {
      return !WildKinds.Contains(kind) && kind != CitySign;
    }

    private static bool AnchorsTown(Neighbour neighbour, Vector3 tile)
    {
      if (Distance(neighbour.Tile, tile) > AnchorRangeMetres)
      {
        return false;
      }

      var isGeneratedBuilding = neighbour.OwnerMasterId == 0 && GeneratedBuildingKinds.Contains(neighbour.Kind);
      var isTownBuilding = neighbour.OwnerMasterId != 0 && neighbour.IsFinished && TownAnchorKinds.Contains(neighbour.Kind);

      return isGeneratedBuilding || isTownBuilding;
    }

    private static bool IsOtherMasters(Neighbour neighbour, long builderMasterId, Vector3 tile)
    {
      return neighbour.OwnerMasterId != 0 && neighbour.OwnerMasterId != builderMasterId &&
             Distance(neighbour.Tile, tile) <= WildSpacingMetres;
    }

    private static float Distance(Vector3 a, Vector3 b)
    {
      return new Vector2(a.X - b.X, a.Z - b.Z).Length();
    }
  }
}
