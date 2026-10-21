using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>
  /// The party this player is in, with every member and, for those online, where they are and how they fare.
  /// <see cref="PartyId"/> 0 with no members means this player is in no party.
  /// </summary>
  [GlobalClass]
  public partial class PartyInfoSMSG : PartySMSG
  {
    [Export] public ulong PartyId { get; set; }

    [Export] public string PartyName { get; set; } = string.Empty;

    [Export] public Godot.Collections.Array<PartyMemberInfo> Members { get; set; } = [];

    /// <summary>True for Even share, false for Each take.</summary>
    [Export] public bool EvenShare { get; set; }

    [Export] public ulong OwnerMasterId { get; set; }

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
        Members = members,
        EvenShare = proto.ExpShare == global::Bnet.PartyExpShare.EvenShare,
        OwnerMasterId = proto.OwnerMasterId
      };
    }
  }
}
