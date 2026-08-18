using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Which client-side catalogue a VisualComponentSMSG's Id points into. Mirrored in GDScript by
  /// Game/Entity/Visual/visual_kind.gd, since an exported C# enum reaches GDScript as a plain int.
  /// </summary>
  public enum VisualKind
  {
    Bestia = 0,
    Item = 1,
    Effect = 2
  }

  /// <summary>
  /// What an entity looks like: a kind plus an id into the matching client catalogue.
  /// MasterVisualComponentSMSG stays separate because a master carries appearance parameters
  /// rather than a single catalogue id.
  /// </summary>
  [GlobalClass]
  public partial class VisualComponentSMSG : EntitySMSG
  {
    [Export] public VisualKind Kind { get; set; } = VisualKind.Bestia;

    [Export] public ulong VisualId { get; set; } = 0;

    public static VisualComponentSMSG FromProto(global::Bnet.VisualComponent protoVisual)
    {
      return new VisualComponentSMSG
      {
        EntityId = protoVisual.EntityId,
        Kind = MapKindFromProto(protoVisual.Kind),
        VisualId = protoVisual.Id
      };
    }

    private static VisualKind MapKindFromProto(global::Bnet.VisualKind protoKind)
    {
      return protoKind switch
      {
        global::Bnet.VisualKind.Bestia => VisualKind.Bestia,
        global::Bnet.VisualKind.Item => VisualKind.Item,
        global::Bnet.VisualKind.Effect => VisualKind.Effect,
        _ => VisualKind.Bestia
      };
    }

    public override string ToString()
    {
      return $"VisualComponentSMSG(EntityId={EntityId}, Kind={Kind}, VisualId={VisualId})";
    }
  }
}
