using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// What each of the master's bestias did since the master last left the world. Sent once, right after login.
  /// Facts only: the client puts them in words.
  /// </summary>
  [GlobalClass]
  public partial class AwayReportSMSG : ISMSG
  {
    [Export] public long SinceEpochMs { get; set; }

    [Export] public Godot.Collections.Array<BestiaAwayReport> Bestias { get; set; } = [];

    public static AwayReportSMSG FromProto(global::Bnet.AwayReportSMSG proto)
    {
      var report = new AwayReportSMSG { SinceEpochMs = proto.SinceEpochMs };
      foreach (var bestia in proto.Bestias)
      {
        report.Bestias.Add(BestiaAwayReport.FromProto(bestia));
      }

      return report;
    }
  }
}
