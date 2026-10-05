using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>Turns a state batch into the entity messages it carries, in the order the server sent them.</summary>
  public static class StateBatchMessages
  {
    public static IEnumerable<EntitySMSG> Unpack(global::Bnet.StateBatchSMSG batch)
    {
      foreach (var update in batch.Updates)
      {
        foreach (var delta in update.Components)
        {
          var msg = FromDelta(update.EntityId, delta);
          if (msg != null)
          {
            yield return msg;
          }
        }

        if (update.Vanish != null)
        {
          yield return VanishEntitySMSG.FromProto(update.EntityId, update.Vanish);
        }
      }
    }

    private static EntitySMSG FromDelta(ulong entityId, global::Bnet.ComponentDelta delta)
    {
      switch (delta.ComponentCase)
      {
        case global::Bnet.ComponentDelta.ComponentOneofCase.Position: return PositionComponent.FromProto(entityId, delta.Position);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Path: return PathComponentSMSG.FromProto(entityId, delta.Path);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Health: return HealthComponentSMSG.FromProto(entityId, delta.Health);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Animation: return AnimationComponentSMSG.FromProto(entityId, delta.Animation);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Speed: return SpeedComponentSMSG.FromProto(entityId, delta.Speed);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Mana: return ManaComponentSMSG.FromProto(entityId, delta.Mana);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Casting: return CastingComponentSMSG.FromProto(entityId, delta.Casting);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Dead: return DeadComponentSMSG.FromProto(entityId, delta.Dead);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Visual: return VisualComponentSMSG.FromProto(entityId, delta.Visual);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Effects: return BuffListSMSG.FromProto(entityId, delta.Effects);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Stamina: return StaminaComponentSMSG.FromProto(entityId, delta.Stamina);
        case global::Bnet.ComponentDelta.ComponentOneofCase.MasterVisual: return MasterVisualComponentSMSG.FromProto(entityId, delta.MasterVisual);
        case global::Bnet.ComponentDelta.ComponentOneofCase.TownsfolkVisual: return TownsfolkVisualComponentSMSG.FromProto(entityId, delta.TownsfolkVisual);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Level: return LevelComponentSMSG.FromBnet(entityId, delta.Level);
        case global::Bnet.ComponentDelta.ComponentOneofCase.AreaName: return AreaNameComponentSMSG.FromProto(entityId, delta.AreaName);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Exp: return ExpComponentSMSG.FromBnet(entityId, delta.Exp);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Inventory: return InventoryComponentSMSG.FromProto(entityId, delta.Inventory);
        case global::Bnet.ComponentDelta.ComponentOneofCase.SkillPoints: return SkillPointsComponentSMSG.FromProto(entityId, delta.SkillPoints);
        case global::Bnet.ComponentDelta.ComponentOneofCase.SkillList: return SkillListSMSG.FromProto(entityId, delta.SkillList);
        case global::Bnet.ComponentDelta.ComponentOneofCase.LogoutIntent: return LogoutIntentComponentSMSG.FromProto(entityId, delta.LogoutIntent);
        case global::Bnet.ComponentDelta.ComponentOneofCase.CarryCapacity: return CarryCapacityComponentSMSG.FromProto(entityId, delta.CarryCapacity);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Equipment: return EquipmentComponentSMSG.FromProto(entityId, delta.Equipment);
        case global::Bnet.ComponentDelta.ComponentOneofCase.StatusValues: return StatusValuesComponentSMSG.FromProto(entityId, delta.StatusValues);
        case global::Bnet.ComponentDelta.ComponentOneofCase.StatusPoints: return StatusPointsComponentSMSG.FromProto(entityId, delta.StatusPoints);
        case global::Bnet.ComponentDelta.ComponentOneofCase.BaseStatusValues: return BaseStatusValuesComponentSMSG.FromProto(entityId, delta.BaseStatusValues);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Place: return PlaceComponentSMSG.FromProto(entityId, delta.Place);
        case global::Bnet.ComponentDelta.ComponentOneofCase.Construction: return ConstructionComponentSMSG.FromProto(entityId, delta.Construction);
        default:
          GD.PrintErr($"StateBatchMessages: component '{delta.ComponentCase}' of entity {entityId} was not handled!");
          return null;
      }
    }
  }
}
