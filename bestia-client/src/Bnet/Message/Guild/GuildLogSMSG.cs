using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>The guild's newest log entries, newest first.</summary>
  [GlobalClass]
  public partial class GuildLogSMSG : GuildSMSG
  {
    [Export] public Godot.Collections.Array<GuildLogEntryInfo> Entries { get; set; } = [];

    public static GuildLogSMSG FromProto(global::Bnet.GuildLogSMSG proto)
    {
      var entries = new Godot.Collections.Array<GuildLogEntryInfo>();
      foreach (var entry in proto.Entries)
      {
        entries.Add(GuildLogEntryInfo.FromProto(entry));
      }

      return new GuildLogSMSG { Entries = entries };
    }
  }
}
