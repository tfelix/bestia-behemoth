using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Where an entity is, in words. Sent to its owner only, and only when the answer changes.
  /// </summary>
  /// <remarks>
  /// One name, because the server decides which place a player is in. Areas overlap on the ground - a town
  /// inside a region, two claims touching - but ranking them is a server rule, and a client that ranked
  /// them itself would be a second implementation of it.
  ///
  /// When the place is a settlement, its ground comes along, so the building rules can tell where it ends.
  ///
  /// The name is English by construction: generated strings cannot pass through the build-time
  /// <c>tr()</c> tables, so only the sentence around a name is ever translated.
  /// </remarks>
  [GlobalClass]
  public partial class PlaceComponentSMSG : EntitySMSG
  {
    /// <summary>What to call where this entity is standing. Empty only for an empty message.</summary>
    [Export] public string Name { get; set; } = "";

    /// <summary>Whether this place is a settlement. Out in the open it is a region's name and nothing else.</summary>
    [Export] public bool InSettlement { get; set; }

    /// <summary>The settlement's centre as a tile, in Godot axes: x and z, with y left at 0.</summary>
    [Export] public Vector3 SettlementCentre { get; set; }

    /// <summary>How far the settlement reaches from its centre, in tiles.</summary>
    [Export] public float SettlementRadius { get; set; }

    public PlaceComponentSMSG()
    {
    }

    public static PlaceComponentSMSG FromProto(ulong entityId, global::Bnet.PlaceComponentSMSG protoPlace)
    {
      var settlement = protoPlace.Settlement;

      return new PlaceComponentSMSG()
      {
        EntityId = entityId,
        Name = protoPlace.Name,
        InSettlement = settlement != null,
        SettlementCentre = settlement != null ? new Vector3(settlement.X, 0f, settlement.Y) : Vector3.Zero,
        SettlementRadius = settlement?.Radius ?? 0f
      };
    }

    public override string ToString()
    {
      return $"PlaceComponentSMSG(EntityId={EntityId}, Name={Name})";
    }
  }
}
