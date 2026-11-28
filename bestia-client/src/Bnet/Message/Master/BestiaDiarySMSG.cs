using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>The diary of the selected master's bestias, newest first, for the chat's Bestias tab.</summary>
  [GlobalClass]
  public partial class BestiaDiarySMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<BestiaNewsSMSG> Lines { get; set; } = [];

    public static BestiaDiarySMSG FromProto(global::Bnet.BestiaDiarySMSG proto)
    {
      var diary = new BestiaDiarySMSG();
      foreach (var line in proto.Lines)
      {
        diary.Lines.Add(BestiaNewsSMSG.FromProto(line));
      }

      return diary;
    }
  }
}
