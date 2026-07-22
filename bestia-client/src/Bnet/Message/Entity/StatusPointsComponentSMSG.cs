using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Status points component message from server containing a bestia master's unspent status points.
  /// </summary>
  [GlobalClass]
  public partial class StatusPointsComponentSMSG : EntitySMSG
  {
    [Export] public uint Points { get; set; }

    public StatusPointsComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create StatusPointsComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoStatusPoints">The protobuf StatusPointsSMSG message from the server</param>
    /// <returns>A new StatusPointsComponentSMSG instance</returns>
    public static StatusPointsComponentSMSG FromProto(global::Bnet.StatusPointsSMSG protoStatusPoints)
    {
      return new StatusPointsComponentSMSG()
      {
        EntityId = protoStatusPoints.EntityId,
        Points = protoStatusPoints.Points
      };
    }

    public override string ToString()
    {
      return $"StatusPointsComponentSMSG(EntityId={EntityId}, Points={Points})";
    }
  }
}
