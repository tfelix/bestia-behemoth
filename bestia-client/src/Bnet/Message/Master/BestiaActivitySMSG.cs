using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// One of the player's bestias does something new. At most every 2 s per bestia.
  /// </summary>
  [GlobalClass]
  public partial class BestiaActivitySMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }

    [Export] public ulong PlayerBestiaId { get; set; }

    [Export] public BestiaActivityInfo Activity { get; set; } = new();

    public static BestiaActivitySMSG FromProto(global::Bnet.BestiaActivitySMSG proto)
    {
      return new BestiaActivitySMSG
      {
        EntityId = proto.EntityId,
        PlayerBestiaId = proto.PlayerBestiaId,
        Activity = proto.Activity == null ? new BestiaActivityInfo() : BestiaActivityInfo.FromProto(proto.Activity)
      };
    }
  }
}
