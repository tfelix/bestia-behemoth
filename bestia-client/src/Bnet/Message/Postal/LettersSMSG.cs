using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The played master's mailbox, newest first.</summary>
  [GlobalClass]
  public partial class LettersSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<LetterEntry> Letters { get; set; } = [];

    public static LettersSMSG FromProto(global::Bnet.LettersSMSG proto)
    {
      var letters = new Godot.Collections.Array<LetterEntry>();
      foreach (var letter in proto.Letters)
      {
        letters.Add(LetterEntry.FromProto(letter));
      }

      return new LettersSMSG { Letters = letters };
    }
  }
}
