using Godot;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>Something happened to a town this master holds or held.</summary>
  [GlobalClass]
  public partial class SettlementNoticeSMSG : ISMSG
  {
    /// <summary><c>bnet.SettlementNoticeSMSG.Notice</c> as a snake_case name, such as <c>fell</c>.</summary>
    [Export] public string NoticeName { get; set; } = "";

    [Export] public string Name { get; set; } = "";

    public static SettlementNoticeSMSG FromProto(global::Bnet.SettlementNoticeSMSG proto)
    {
      return new SettlementNoticeSMSG { NoticeName = EnumName.Of(proto.Notice), Name = proto.Name };
    }
  }
}
