using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>A Trade Agreement of this master began or ended.</summary>
  [GlobalClass]
  public partial class TradeLinkNoticeSMSG : ISMSG
  {
    /// <summary><c>bnet.TradeLinkNoticeSMSG.Notice</c> as a snake_case name, such as <c>linked</c>.</summary>
    [Export] public string NoticeName { get; set; } = "";

    [Export] public string PartnerName { get; set; } = "";

    public static TradeLinkNoticeSMSG FromProto(global::Bnet.TradeLinkNoticeSMSG proto)
    {
      return new TradeLinkNoticeSMSG { NoticeName = EnumName.Of(proto.Notice), PartnerName = proto.PartnerName };
    }
  }
}
