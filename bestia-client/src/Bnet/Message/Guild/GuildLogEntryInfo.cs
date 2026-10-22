using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>One entry of a <see cref="GuildLogSMSG"/>. Its own file because Godot registers one <c>[GlobalClass]</c> per file.</summary>
  [GlobalClass]
  public partial class GuildLogEntryInfo : GodotObject
  {
    /// <summary><c>bnet.GuildLogKind</c> as a snake_case name, such as <c>rank_set</c>.</summary>
    [Export] public string KindName { get; set; } = string.Empty;

    [Export] public long AtEpochMs { get; set; }

    [Export] public string ActorName { get; set; } = string.Empty;

    /// <summary>Empty when the entry is about the actor alone.</summary>
    [Export] public string TargetName { get; set; } = string.Empty;

    /// <summary>A rank's name, or a skills.yml id for <c>skill_learned</c>.</summary>
    [Export] public string Detail { get; set; } = string.Empty;

    public static GuildLogEntryInfo FromProto(global::Bnet.GuildLogEntry proto)
    {
      return new GuildLogEntryInfo
      {
        KindName = EnumName.Of(proto.Kind),
        AtEpochMs = proto.AtEpochMs,
        ActorName = proto.ActorName,
        TargetName = proto.TargetName,
        Detail = proto.Detail
      };
    }
  }
}
