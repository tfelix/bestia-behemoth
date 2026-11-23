using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>Something every connected player reads: a GM's words, or news of the world.</summary>
  [GlobalClass]
  public partial class AnnouncementSMSG : ISMSG
  {
    /// <summary><c>bnet.Announcement</c> as a snake_case name, such as <c>stronghold_unheld</c>.</summary>
    [Export] public string AnnouncementName { get; set; } = string.Empty;

    /// <summary>Substitution values for the announcement's text, in order.</summary>
    [Export] public Godot.Collections.Array<string> Args { get; set; } = [];

    public static AnnouncementSMSG FromProto(global::Bnet.AnnouncementSMSG proto)
    {
      var args = new Godot.Collections.Array<string>();
      foreach (var arg in proto.Args)
      {
        args.Add(arg);
      }

      return new AnnouncementSMSG
      {
        AnnouncementName = EnumName.Of(proto.Announcement),
        Args = args
      };
    }
  }
}
