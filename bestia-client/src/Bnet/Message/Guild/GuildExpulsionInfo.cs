using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>One expelled master of a <see cref="GuildInfoSMSG"/>. Its own file because Godot registers one <c>[GlobalClass]</c> per file.</summary>
  [GlobalClass]
  public partial class GuildExpulsionInfo : GodotObject
  {
    [Export] public ulong MasterId { get; set; }

    [Export] public string MasterName { get; set; } = string.Empty;

    [Export] public string Reason { get; set; } = string.Empty;

    [Export] public long AtEpochMs { get; set; }

    public static GuildExpulsionInfo FromProto(global::Bnet.GuildExpulsion proto)
    {
      return new GuildExpulsionInfo
      {
        MasterId = proto.MasterId,
        MasterName = proto.MasterName,
        Reason = proto.Reason,
        AtEpochMs = proto.AtEpochMs
      };
    }
  }
}
