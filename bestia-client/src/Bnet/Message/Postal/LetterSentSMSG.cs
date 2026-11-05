using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The pigeon took the letter.</summary>
  [GlobalClass]
  public partial class LetterSentSMSG : ISMSG
  {
    [Export] public string RecipientName { get; set; } = "";

    public static LetterSentSMSG FromProto(global::Bnet.LetterSentSMSG proto)
    {
      return new LetterSentSMSG { RecipientName = proto.RecipientName };
    }
  }
}
