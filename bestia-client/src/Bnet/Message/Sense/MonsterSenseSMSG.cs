using Godot;

namespace BestiaBehemothClient.Bnet.Message.Sense
{
  /// <summary>What the Sense skill read off a monster. Element, kind and size are the server's enum names.</summary>
  [GlobalClass]
  public partial class MonsterSenseSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }
    [Export] public int Level { get; set; }
    [Export] public string Element { get; set; } = "";
    [Export] public string Kind { get; set; } = "";
    [Export] public string Size { get; set; } = "";
    [Export] public int Hp { get; set; }
    [Export] public int MaxHp { get; set; }
    [Export] public int Mana { get; set; }
    [Export] public int MaxMana { get; set; }
    [Export] public int Strength { get; set; }
    [Export] public int Vitality { get; set; }
    [Export] public int Intelligence { get; set; }
    [Export] public int Agility { get; set; }
    [Export] public int Willpower { get; set; }
    [Export] public int Dexterity { get; set; }
    [Export] public int Defense { get; set; }
    [Export] public int MagicDefense { get; set; }

    public static MonsterSenseSMSG FromProto(global::Bnet.MonsterSenseSMSG proto)
    {
      return new MonsterSenseSMSG
      {
        EntityId = proto.EntityId,
        Level = proto.Level,
        Element = proto.Element,
        Kind = proto.Kind,
        Size = proto.Size,
        Hp = proto.Hp,
        MaxHp = proto.MaxHp,
        Mana = proto.Mana,
        MaxMana = proto.MaxMana,
        Strength = proto.Strength,
        Vitality = proto.Vitality,
        Intelligence = proto.Intelligence,
        Agility = proto.Agility,
        Willpower = proto.Willpower,
        Dexterity = proto.Dexterity,
        Defense = proto.Defense,
        MagicDefense = proto.MagicDefense
      };
    }
  }
}
