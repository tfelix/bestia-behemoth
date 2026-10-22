using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Something happened to the guild that every member should read about.</summary>
  [GlobalClass]
  public partial class GuildNoticeSMSG : GuildSMSG
  {
    /// <summary><c>bnet.GuildNotice</c> as a snake_case name, such as <c>banner_fallen</c>.</summary>
    [Export] public string NoticeName { get; set; } = string.Empty;

    public static GuildNoticeSMSG FromProto(global::Bnet.GuildNoticeSMSG proto)
    {
      return new GuildNoticeSMSG
      {
        NoticeName = EnumName.Of(proto.Notice)
      };
    }
  }
}
