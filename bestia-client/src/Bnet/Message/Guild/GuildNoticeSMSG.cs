using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Something happened to the guild that every member should read about.</summary>
  [GlobalClass]
  public partial class GuildNoticeSMSG : GuildSMSG
  {
    /// <summary><c>bnet.GuildNotice</c> as a snake_case name, such as <c>banner_fallen</c>.</summary>
    [Export] public string NoticeName { get; set; } = string.Empty;

    /// <summary>Substitution values for the notice's text, in order, such as a Stronghold's name.</summary>
    [Export] public Godot.Collections.Array<string> Args { get; set; } = [];

    public static GuildNoticeSMSG FromProto(global::Bnet.GuildNoticeSMSG proto)
    {
      var args = new Godot.Collections.Array<string>();
      foreach (var arg in proto.Args)
      {
        args.Add(arg);
      }

      return new GuildNoticeSMSG
      {
        NoticeName = EnumName.Of(proto.Notice),
        Args = args
      };
    }
  }
}
