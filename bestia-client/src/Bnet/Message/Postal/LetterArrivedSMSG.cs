using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>A pigeon brought a letter to the played master.</summary>
  [GlobalClass]
  public partial class LetterArrivedSMSG : ISMSG
  {
    [Export] public LetterEntry Letter { get; set; }

    public static LetterArrivedSMSG FromProto(global::Bnet.LetterArrivedSMSG proto)
    {
      return new LetterArrivedSMSG { Letter = LetterEntry.FromProto(proto.Letter) };
    }
  }
}
