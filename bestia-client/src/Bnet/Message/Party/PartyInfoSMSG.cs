using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party this player is in, with every member and, for those online, where they are.</summary>
  [GlobalClass]
  public partial class PartyInfoSMSG : PartySMSG
  {
    [Export] public ulong PartyId { get; set; }

    [Export] public string PartyName { get; set; } = string.Empty;

    [Export] public Godot.Collections.Array<PartyMemberInfo> Members { get; set; } = [];

    public static PartyInfoSMSG FromProto(global::Bnet.PartyInfoSMSG proto)
    {
      var members = new Godot.Collections.Array<PartyMemberInfo>();
      foreach (var member in proto.Member)
      {
        members.Add(PartyMemberInfo.FromProto(member));
      }

      return new PartyInfoSMSG
      {
        PartyId = proto.PartyId,
        PartyName = proto.PartyName,
        Members = members
      };
    }
  }
}
