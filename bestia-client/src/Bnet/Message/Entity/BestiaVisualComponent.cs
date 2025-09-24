using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's bestia visual component.
  /// Contains the entity ID and bestia visual data.
  /// </summary>
  [GlobalClass]
  public partial class BestiaVisualComponent : EntitySMSG
  {
    [Export]
    public long BestiaId { get; set; } = 0;

    public static BestiaVisualComponent FromProto(global::Bnet.BestiaVisualComponent protoBestiaVisual)
    {
      return new BestiaVisualComponent
      {
        EntityId = protoBestiaVisual.EntityId,
        BestiaId = protoBestiaVisual.BestiaId
      };
    }
  }
}