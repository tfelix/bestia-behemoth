using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Survey
{
  /// <summary>When the player stopped the needle in each round, in milliseconds since that round began.</summary>
  public partial class SurveyStopsCMSG : ICMSG
  {
    [Export] public ulong StructureId { get; set; }

    [Export] public long[] StopsMs { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var stops = new global::Bnet.SurveyStopsCMSG { StructureId = StructureId };
      stops.StopsMs.AddRange(StopsMs);

      return new Envelope { SurveyStops = stops };
    }
  }
}
