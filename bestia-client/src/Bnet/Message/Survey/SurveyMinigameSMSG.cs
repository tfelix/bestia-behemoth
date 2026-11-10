using Godot;

namespace BestiaBehemothClient.Bnet.Message.Survey
{
  /// <summary>
  /// The needle game a Cartography cast opened at a theodolite. The needle points at
  /// 90 * sin(2 pi t / <see cref="SwingPeriodMs"/>) degrees; each round is won inside its arc.
  /// </summary>
  [GlobalClass]
  public partial class SurveyMinigameSMSG : ISMSG
  {
    [Export] public ulong StructureId { get; set; }

    [Export] public int SwingPeriodMs { get; set; }

    [Export] public int ArcWidthDegrees { get; set; }

    /// <summary>One arc per round, in degrees from straight up.</summary>
    [Export] public int[] ArcCentresDegrees { get; set; } = [];

    public static SurveyMinigameSMSG FromProto(global::Bnet.SurveyMinigameSMSG proto)
    {
      var centres = new int[proto.ArcCentresDegrees.Count];
      proto.ArcCentresDegrees.CopyTo(centres, 0);

      return new SurveyMinigameSMSG
      {
        StructureId = proto.StructureId,
        SwingPeriodMs = (int)proto.SwingPeriodMs,
        ArcWidthDegrees = (int)proto.ArcWidthDegrees,
        ArcCentresDegrees = centres
      };
    }
  }
}
