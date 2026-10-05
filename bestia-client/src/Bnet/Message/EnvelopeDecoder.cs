using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Bnet;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Turns a received envelope into the message object the game listens for. Every case the server sends has
  /// an entry here, or is listed in <see cref="EnvelopeCases.NotUsedYet"/>; <c>EnvelopeDecoderTest</c> checks it.
  /// </summary>
  public static class EnvelopeDecoder
  {
    private static readonly Dictionary<Envelope.MessageOneofCase, Func<Envelope, ISMSG>> Routes = new()
    {
      [Envelope.MessageOneofCase.AuthenticationSuccess] = e => AuthenticationSuccess.FromProto(e.AuthenticationSuccess),
      [Envelope.MessageOneofCase.Pong] = _ => new Pong(),
      [Envelope.MessageOneofCase.Master] = e => Master.MasterSMSG.FromProto(e.Master),
      [Envelope.MessageOneofCase.DamageEntity] = e => Entity.DamageEntitySMSG.FromProto(e.DamageEntity),
      [Envelope.MessageOneofCase.CompMana] = e => Entity.ManaComponentSMSG.FromProto(e.CompMana),
      [Envelope.MessageOneofCase.CompHealth] = e => Entity.HealthComponentSMSG.FromProto(e.CompHealth),
      [Envelope.MessageOneofCase.CompCasting] = e => Entity.CastingComponentSMSG.FromProto(e.CompCasting),
      [Envelope.MessageOneofCase.CompConstruction] =
        e => Entity.ConstructionComponentSMSG.FromProto(e.CompConstruction),
      [Envelope.MessageOneofCase.CompStamina] = e => Entity.StaminaComponentSMSG.FromProto(e.CompStamina),
      [Envelope.MessageOneofCase.CompPlace] = e => Entity.PlaceComponentSMSG.FromProto(e.CompPlace),
      [Envelope.MessageOneofCase.CompAreaName] = e => Entity.AreaNameComponentSMSG.FromProto(e.CompAreaName),
      [Envelope.MessageOneofCase.CompCarryCapacity] =
        e => Entity.CarryCapacityComponentSMSG.FromProto(e.CompCarryCapacity),
      [Envelope.MessageOneofCase.CompEffects] = e => Entity.BuffListSMSG.FromProto(e.CompEffects),
      [Envelope.MessageOneofCase.CompInventory] = e => Entity.InventoryComponentSMSG.FromProto(e.CompInventory),
      [Envelope.MessageOneofCase.CompEquipment] = e => Entity.EquipmentComponentSMSG.FromProto(e.CompEquipment),
      [Envelope.MessageOneofCase.CompSkillList] = e => Entity.SkillListSMSG.FromProto(e.CompSkillList),
      [Envelope.MessageOneofCase.CompSkillPoints] = e => Entity.SkillPointsComponentSMSG.FromProto(e.CompSkillPoints),
      [Envelope.MessageOneofCase.CompStatusValues] =
        e => Entity.StatusValuesComponentSMSG.FromProto(e.CompStatusValues),
      [Envelope.MessageOneofCase.CompBaseStatusValues] =
        e => Entity.BaseStatusValuesComponentSMSG.FromProto(e.CompBaseStatusValues),
      [Envelope.MessageOneofCase.CompStatusPoints] =
        e => Entity.StatusPointsComponentSMSG.FromProto(e.CompStatusPoints),
      [Envelope.MessageOneofCase.CompVisual] = e => Entity.VisualComponentSMSG.FromProto(e.CompVisual),
      [Envelope.MessageOneofCase.Self] = e => Master.SelfSMSG.FromProto(e.Self),
      [Envelope.MessageOneofCase.OwnedBestias] = e => Master.OwnedBestiasSMSG.FromProto(e.OwnedBestias),
      [Envelope.MessageOneofCase.ActiveEntity] = e => Master.ActiveEntitySMSG.FromProto(e.ActiveEntity),
      [Envelope.MessageOneofCase.BestiaCapture] = e => Master.BestiaCaptureSMSG.FromProto(e.BestiaCapture),
      [Envelope.MessageOneofCase.CompLevel] = e => Entity.LevelComponentSMSG.FromBnet(e.CompLevel),
      [Envelope.MessageOneofCase.CompExp] = e => Entity.ExpComponentSMSG.FromBnet(e.CompExp),
      [Envelope.MessageOneofCase.CompMasterVisual] =
        e => Entity.MasterVisualComponentSMSG.FromProto(e.CompMasterVisual),
      [Envelope.MessageOneofCase.CompTownsfolkVisual] =
        e => Entity.TownsfolkVisualComponentSMSG.FromProto(e.CompTownsfolkVisual),
      [Envelope.MessageOneofCase.VanishEntity] = e => Entity.VanishEntitySMSG.FromProto(e.VanishEntity),
      [Envelope.MessageOneofCase.CompLogoutIntent] =
        e => Entity.LogoutIntentComponentSMSG.FromProto(e.CompLogoutIntent),
      [Envelope.MessageOneofCase.CompDead] = e => Entity.DeadComponentSMSG.FromProto(e.CompDead),
      [Envelope.MessageOneofCase.CompPosition] = e => Entity.PositionComponent.FromProto(e.CompPosition),
      [Envelope.MessageOneofCase.CompPath] = e => Entity.PathComponentSMSG.FromProto(e.CompPath),
      [Envelope.MessageOneofCase.CompSpeed] = e => Entity.SpeedComponentSMSG.FromProto(e.CompSpeed),
      [Envelope.MessageOneofCase.CompAnimation] = e => Entity.AnimationComponentSMSG.FromProto(e.CompAnimation),
      [Envelope.MessageOneofCase.OperationSuccess] = e => OperationSuccess.FromProto(e.OperationSuccess),
      [Envelope.MessageOneofCase.OperationError] = e => OperationError.FromProto(e.OperationError),
      [Envelope.MessageOneofCase.ChatSmsg] = e => System.ChatSMSG.FromProto(e.ChatSmsg),
      [Envelope.MessageOneofCase.Dialog] = e => System.DialogSMSG.FromProto(e.Dialog),
      [Envelope.MessageOneofCase.Conversation] = e => System.ConversationSMSG.FromProto(e.Conversation),
      [Envelope.MessageOneofCase.CraftableRecipes] = e => Crafting.CraftableRecipesSMSG.FromProto(e.CraftableRecipes),
      [Envelope.MessageOneofCase.WorldInfo] = e => Map.WorldInfoSMSG.FromProto(e.WorldInfo),
      [Envelope.MessageOneofCase.WorldTime] = e => Map.WorldTimeSMSG.FromProto(e.WorldTime),
      [Envelope.MessageOneofCase.ChunkManifest] = e => Map.ChunkManifestSMSG.FromProto(e.ChunkManifest),
      // Converted but not decoded. Decoding here would put a whole login's worth of chunks into the one
      // frame that drains the queue; ChunkStreamManager spreads it instead.
      [Envelope.MessageOneofCase.ChunkData] = e => Map.ChunkDataSMSG.FromProto(e.ChunkData),
      [Envelope.MessageOneofCase.ChunkPatch] = e => Map.ChunkPatchSMSG.FromProto(e.ChunkPatch),
      [Envelope.MessageOneofCase.Weather] = e => Map.WeatherSMSG.FromProto(e.Weather),
      // A MapSMSG, so ConnectionManager's GDScript handler passes it over and EntityManager never sees it.
      // See ChunkStaticEntitiesSMSG: routing these through the entity path would instantiate a full
      // Entity.tscn per tree.
      [Envelope.MessageOneofCase.ChunkStaticEntities] =
        e => Map.ChunkStaticEntitiesSMSG.FromProto(e.ChunkStaticEntities),
      // A MapSMSG for ChunkStaticEntitiesSMSG's reason: it describes ground, not an entity this client ever
      // spawned, so EntityManager must never see it.
      [Envelope.MessageOneofCase.ChunkGroundOverlay] =
        e => Map.ChunkGroundOverlaySMSG.FromProto(e.ChunkGroundOverlay, WorldLayout.ChunkSize),
      // Also a MapSMSG: lasting marks on the ground, not an entity.
      [Envelope.MessageOneofCase.ChunkGroundLayers] =
        e => Map.ChunkGroundLayersSMSG.FromProto(e.ChunkGroundLayers, WorldLayout.ChunkSize),
      // Also a MapSMSG: what walked over the ground, not an entity.
      [Envelope.MessageOneofCase.ChunkGroundStamps] = e => Map.ChunkGroundStampsSMSG.FromProto(e.ChunkGroundStamps),
      // Also a MapSMSG, and for the same reason: it names a prop, not an entity the client ever spawned.
      [Envelope.MessageOneofCase.StaticEntityRemoved] =
        e => Map.StaticEntityRemovedSMSG.FromProto(e.StaticEntityRemoved),
      [Envelope.MessageOneofCase.TradeRequest] = e => Trade.TradeRequestSMSG.FromProto(e.TradeRequest),
      [Envelope.MessageOneofCase.TradeState] = e => Trade.TradeStateSMSG.FromProto(e.TradeState),
      [Envelope.MessageOneofCase.ShopOffer] = e => Shop.ShopOfferSMSG.FromProto(e.ShopOffer),
      [Envelope.MessageOneofCase.PartyInvitation] = e => Party.PartyInvitationSMSG.FromProto(e.PartyInvitation),
      [Envelope.MessageOneofCase.PartyInvitationCreated] =
        e => Party.PartyInvitationCreatedSMSG.FromProto(e.PartyInvitationCreated),
      [Envelope.MessageOneofCase.PartyInviteDeclined] =
        e => Party.PartyInviteDeclinedSMSG.FromProto(e.PartyInviteDeclined),
      [Envelope.MessageOneofCase.PartyInfo] = e => Party.PartyInfoSMSG.FromProto(e.PartyInfo),
      [Envelope.MessageOneofCase.PartyError] = e => Party.PartyErrorSMSG.FromProto(e.PartyError),
      [Envelope.MessageOneofCase.DisbandParty] = e => Party.DisbandPartySMSG.FromProto(e.DisbandParty),
    };

    public static IReadOnlyCollection<Envelope.MessageOneofCase> RoutedCases => Routes.Keys;

    /// <summary>The message for <paramref name="envelope"/>, or null for a case without a route.</summary>
    public static ISMSG Decode(Envelope envelope)
    {
      return Routes.TryGetValue(envelope.MessageCase, out var route) ? route(envelope) : null;
    }
  }
}
