using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>
  /// One member of a <see cref="PartyInfoSMSG"/>. Its own file because Godot registers one
  /// <c>[GlobalClass]</c> per C# file.
  ///
  /// The bestia the member plays is flattened into <c>ActiveBestia*</c> rather than nested: every wrapper here
  /// exports arrays of objects, none a single object, and a flat one is what GDScript reads plainly.
  /// </summary>
  [GlobalClass]
  public partial class PartyMemberInfo : GodotObject
  {
    [Export] public string MasterName { get; set; } = string.Empty;

    [Export] public ulong AccountId { get; set; }

    [Export] public ulong MasterId { get; set; }

    [Export] public uint Level { get; set; }

    /// <summary>False for a member who is offline; every field below is then empty.</summary>
    [Export] public bool IsOnline { get; set; }

    /// <summary>The master's entity, whose Health and Mana updates reach this player wherever it stands.</summary>
    [Export] public ulong EntityId { get; set; }

    [Export] public string AreaName { get; set; } = string.Empty;

    [Export] public Vector3 Position { get; set; }

    [Export] public uint HpCurrent { get; set; }

    [Export] public uint HpMax { get; set; }

    [Export] public uint ManaCurrent { get; set; }

    [Export] public uint ManaMax { get; set; }

    /// <summary>The bestia the member plays, or 0 while they play their master.</summary>
    [Export] public ulong ActiveBestiaEntityId { get; set; }

    [Export] public ulong ActiveBestiaSpeciesId { get; set; }

    /// <summary>The name its master gave it, empty when it has none.</summary>
    [Export] public string ActiveBestiaName { get; set; } = string.Empty;

    [Export] public uint ActiveBestiaLevel { get; set; }

    [Export] public uint ActiveBestiaHpCurrent { get; set; }

    [Export] public uint ActiveBestiaHpMax { get; set; }

    [Export] public uint ActiveBestiaManaCurrent { get; set; }

    [Export] public uint ActiveBestiaManaMax { get; set; }

    public static PartyMemberInfo FromProto(global::Bnet.PartyMember proto)
    {
      var info = new PartyMemberInfo
      {
        MasterName = proto.MasterName,
        AccountId = proto.AccountId,
        MasterId = proto.MasterId,
        Level = proto.Level
      };

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
      info.ManaCurrent = online.ManaCurrent;
      info.ManaMax = online.ManaMax;

      // Server z-up to Godot y-up.
      if (online.Position != null)
      {
        info.Position = new Vector3((float)online.Position.X, (float)online.Position.Z, (float)online.Position.Y);
      }

      var bestia = online.ActiveBestia;
      if (bestia != null)
      {
        info.ActiveBestiaEntityId = bestia.EntityId;
        info.ActiveBestiaSpeciesId = bestia.SpeciesId;
        info.ActiveBestiaName = bestia.Name;
        info.ActiveBestiaLevel = bestia.Level;
        info.ActiveBestiaHpCurrent = bestia.HpCurrent;
        info.ActiveBestiaHpMax = bestia.HpMax;
        info.ActiveBestiaManaCurrent = bestia.ManaCurrent;
        info.ActiveBestiaManaMax = bestia.ManaMax;
      }

      return info;
    }
  }
}
