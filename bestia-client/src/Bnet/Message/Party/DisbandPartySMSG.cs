using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party this player was in no longer exists.</summary>
  [GlobalClass]
  public partial class DisbandPartySMSG : PartySMSG
  {
    [Export] public ulong PartyId { get; set; }

    public static DisbandPartySMSG FromProto(global::Bnet.DisbandPartySMSG proto)
    {
      return new DisbandPartySMSG { PartyId = proto.PartyId };
    }
  }
}
