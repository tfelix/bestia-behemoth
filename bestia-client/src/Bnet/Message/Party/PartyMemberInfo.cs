using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>
  /// One member of a <see cref="PartyInfoSMSG"/>. Its own file because Godot registers one
  /// <c>[GlobalClass]</c> per C# file.
  /// </summary>
  [GlobalClass]
  public partial class PartyMemberInfo : GodotObject
  {
    [Export] public string MasterName { get; set; } = string.Empty;

    /// <summary>False for a member who is offline; every field below is then empty.</summary>
    [Export] public bool IsOnline { get; set; }

    [Export] public ulong EntityId { get; set; }

    [Export] public string AreaName { get; set; } = string.Empty;

    [Export] public Vector3 Position { get; set; }

    [Export] public uint HpCurrent { get; set; }

    [Export] public uint HpMax { get; set; }

    public static PartyMemberInfo FromProto(global::Bnet.PartyMember proto)
    {
      var info = new PartyMemberInfo { MasterName = proto.MasterName };

      var online = proto.OnlineData;
      if (online == null)
      {
        return info;
      }

      info.IsOnline = true;
      info.EntityId = online.EntityId;
      info.AreaName = online.AreaName;
      info.HpCurrent = online.HpCurrent;
      info.HpMax = online.HpMax;

      // Server z-up to Godot y-up.
      if (online.Position != null)
      {
        info.Position = new Vector3((float)online.Position.X, (float)online.Position.Z, (float)online.Position.Y);
      }

      return info;
    }
  }
}
