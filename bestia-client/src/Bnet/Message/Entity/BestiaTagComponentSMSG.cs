using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A player bestia's name tag: the name its owner gave it, empty for its species' name, and whose it is.
  /// </summary>
  [GlobalClass]
  public partial class BestiaTagComponentSMSG : EntitySMSG
  {
    [Export] public string Name { get; set; } = "";

    [Export] public string OwnerName { get; set; } = "";

    public static BestiaTagComponentSMSG FromProto(ulong entityId, global::Bnet.BestiaTagComponentSMSG proto)
    {
      return new BestiaTagComponentSMSG
      {
        EntityId = entityId,
        Name = proto.Name,
        OwnerName = proto.OwnerName
      };
    }

    public override string ToString()
    {
      return $"BestiaTagComponentSMSG(EntityId={EntityId}, Name={Name}, OwnerName={OwnerName})";
    }
  }
}
