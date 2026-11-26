using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>The world ends: the client plays its final sequence and shows what the world lived through.</summary>
  [GlobalClass]
  public partial class WorldEndSMSG : ISMSG
  {
    /// <summary><c>bnet.WorldEnding</c> as a snake_case name: <c>lost</c> or <c>saved</c>.</summary>
    [Export] public string EndingName { get; set; } = string.Empty;

    [Export] public string WorldName { get; set; } = string.Empty;

    /// <summary>The doomsday's kind, such as <c>VOID_TEAR</c>. Empty when no threat came.</summary>
    [Export] public string Threat { get; set; } = string.Empty;

    [Export] public int DaysLived { get; set; }

    /// <summary>How far into its foretold life span the world lived, in percent.</summary>
    [Export] public int TPercent { get; set; }

    [Export] public int TownsLost { get; set; }

    /// <summary>Until the server closes the connection.</summary>
    [Export] public int SecondsLeft { get; set; }

    public static WorldEndSMSG FromProto(global::Bnet.WorldEndSMSG proto)
    {
      return new WorldEndSMSG
      {
        EndingName = EnumName.Of(proto.Ending),
        WorldName = proto.WorldName,
        Threat = proto.Threat,
        DaysLived = proto.DaysLived,
        TPercent = proto.TPercent,
        TownsLost = proto.TownsLost,
        SecondsLeft = proto.SecondsLeft
      };
    }
  }
}
