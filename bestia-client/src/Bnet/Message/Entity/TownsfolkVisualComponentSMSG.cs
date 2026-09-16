using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Who an inhabitant of a settlement is: what they are called, and which body draws them.
  /// </summary>
  /// <remarks>
  /// Arrives instead of <see cref="VisualComponentSMSG"/>, not alongside it. Townsfolk share one
  /// archetype's behaviour, so a species id could only ever say "a townsperson" - which is what every
  /// one of them was labelled before this message existed.
  /// </remarks>
  [GlobalClass]
  public partial class TownsfolkVisualComponentSMSG : EntitySMSG
  {
    [Export] public string Name { get; set; } = "";

    /// <summary>Maps to the TownsfolkBody enum: 0 adult, 1 child.</summary>
    [Export] public int Body { get; set; } = 0;

    public TownsfolkVisualComponentSMSG()
    {
    }

    public static TownsfolkVisualComponentSMSG FromProto(global::Bnet.TownsfolkVisualComponentSMSG protoTownsfolkVisual)
    {
      return new TownsfolkVisualComponentSMSG()
      {
        EntityId = protoTownsfolkVisual.EntityId,
        Name = protoTownsfolkVisual.Name,
        Body = (int)protoTownsfolkVisual.Body
      };
    }

    public override string ToString()
    {
      return $"TownsfolkVisualComponentSMSG(EntityId={EntityId}, Name={Name}, Body={Body})";
    }
  }
}
