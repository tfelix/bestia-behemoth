using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// The entity the player now controls.
  /// </summary>
  [GlobalClass]
  public partial class ActiveEntitySMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }

    public static ActiveEntitySMSG FromProto(global::Bnet.ActiveEntitySMSG proto)
    {
      return new ActiveEntitySMSG
      {
        EntityId = proto.EntityId
      };
    }
  }
}
