using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The name on an area entity itself - a town gate, a claim stone - public to anyone in range.
  /// </summary>
  /// <remarks>
  /// Distinct from <see cref="PlaceComponentSMSG"/>, which privately tells one player where they are
  /// standing. This is a label on a thing in the world; that is an answer about a player.
  /// </remarks>
  [GlobalClass]
  public partial class AreaNameComponentSMSG : EntitySMSG
  {
    [Export] public string Name { get; set; } = "";

    /// <summary>How far the name reaches from the entity, in metres.</summary>
    [Export] public ulong Radius { get; set; }

    public AreaNameComponentSMSG()
    {
    }

    public static AreaNameComponentSMSG FromProto(global::Bnet.AreaNameComponentSMSG protoAreaName)
    {
      return new AreaNameComponentSMSG()
      {
        EntityId = protoAreaName.EntityId,
        Name = protoAreaName.Name,
        Radius = protoAreaName.Radius
      };
    }

    public override string ToString()
    {
      return $"AreaNameComponentSMSG(EntityId={EntityId}, Name={Name}, Radius={Radius})";
    }
  }
}
