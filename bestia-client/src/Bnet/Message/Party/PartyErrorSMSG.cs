using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>A party request was refused.</summary>
  [GlobalClass]
  public partial class PartyErrorSMSG : PartySMSG
  {
    /// <summary><c>bnet.PartyErrorCode</c> as a snake_case name, for the GDScript side.</summary>
    [Export] public string ErrorName { get; set; } = string.Empty;

    public static PartyErrorSMSG FromProto(global::Bnet.PartyErrorSMSG proto)
    {
      return new PartyErrorSMSG { ErrorName = EnumName.Of(proto.Error) };
    }
  }
}
