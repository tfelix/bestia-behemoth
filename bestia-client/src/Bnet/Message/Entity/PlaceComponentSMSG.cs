using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Where an entity is, in words. Sent to its owner only, and only when the answer changes.
  /// </summary>
  /// <remarks>
  /// One name, because the server decides which place a player is in. Areas overlap on the ground - a town
  /// inside a region, two claims touching - but ranking them is a server rule, and a client that ranked
  /// them itself would be a second implementation of it. Nothing here says whether the name came from a
  /// region, a town or a claim: a name is a name to whoever reads it.
  ///
  /// The name is English by construction: generated strings cannot pass through the build-time
  /// <c>tr()</c> tables, so only the sentence around a name is ever translated.
  /// </remarks>
  [GlobalClass]
  public partial class PlaceComponentSMSG : EntitySMSG
  {
    /// <summary>What to call where this entity is standing. Empty only for an empty message.</summary>
    [Export] public string Name { get; set; } = "";

    public PlaceComponentSMSG()
    {
    }

    public static PlaceComponentSMSG FromProto(global::Bnet.PlaceComponentSMSG protoPlace)
    {
      return new PlaceComponentSMSG()
      {
        EntityId = protoPlace.EntityId,
        Name = protoPlace.Name
      };
    }

    public override string ToString()
    {
      return $"PlaceComponentSMSG(EntityId={EntityId}, Name={Name})";
    }
  }
}
