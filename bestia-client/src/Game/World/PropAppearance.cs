using System;
using System.Linq;
using BestiaBehemothClient.Bnet.Message;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// How each of the server's <c>StaticEntityKind</c>s is drawn.
  /// </summary>
  /// <remarks>
  /// A hand-mirrored C# table, on exactly the precedent <see cref="Mesh.BlockAppearance"/> sets for the block
  /// palette: what a thing looks like is a client decision, a new kind needs a client release regardless
  /// because nothing here can invent an appearance for it, and the version handshake is what catches a
  /// disagreement. The ordinals must stay in step with <c>StaticEntityKind.kt</c>, which is append-only for
  /// the same reason.
  ///
  /// <para>
  /// <b>Three ways to draw a prop, and which one a kind gets is what sort of art it has.</b>
  /// </para>
  ///
  /// <list type="bullet">
  /// <item>
  /// A kind with <see cref="Kind.Scenes"/> is instantiated per prop, so it can be a real model with a
  /// trunk, a canopy and eventually an animation. One node and one draw call each, which is why only the
  /// trees have it.
  /// </item>
  /// <item>
  /// A kind with a <see cref="Kind.MeshPath"/> is one mesh batched into a <c>MultiMesh</c>: a single draw
  /// call for every prop of that kind in a chunk, with nothing per instance but a transform. The right shape
  /// for anything that comes in hundreds and does not need moving parts, which is what the ground cover is.
  /// </item>
  /// <item>
  /// A kind with neither falls back to a placeholder box, batched the same way - crude, cheap, and
  /// deliberately a different width and colour per kind so that a wrong-kind bug is visible rather than
  /// merely wrong.
  /// </item>
  /// </list>
  ///
  /// <para>
  /// Giving a kind art is filling in the paths and a natural height on its row here and nothing else.
  /// </para>
  /// </remarks>
  public static class PropAppearance
  {
    /// <summary>How one kind is drawn.</summary>
    /// <summary>What a click on a prop of some kind is for.</summary>
    /// <remarks>
    /// Crossed into GDScript by name rather than as an ordinal, on the bridge <see cref="EnumName"/>
    /// documents: nothing anchors this enum's numbering to <c>DefaultAction.Kind</c>'s, and a name that side
    /// does not know costs a plain pointer, where a stale ordinal would confidently show the wrong tool.
    /// </remarks>
    public enum PropAction
    {
      None,

      /// <summary>Picked up whole - a herb, a shrub. Sends <c>CollectPropCMSG</c>.</summary>
      Collect,

      /// <summary>Also collected, but read as ore: the crystals and the aetherite shards.</summary>
      Mine,

      /// <summary>
      /// Felled by damaging it. <c>PropPromotionService</c> gives the prop <c>Health</c> off its
      /// <c>prop-kinds.yml</c> max-hp the first time something names it, so the click sends an ordinary
      /// <c>AttackEntityCMSG</c>.
      /// </summary>
      Chop,

      /// <summary>A station a click uses. Sends <c>InteractEntityCMSG</c>.</summary>
      Use,

      /// <summary>
      /// Broken by damaging it, like <see cref="Chop"/>, but read as rock: a boulder. Sends the same
      /// <c>AttackEntityCMSG</c>; only the cursor differs.
      /// </summary>
      Break,
    }

    /// <summary>The name <see cref="PropAction"/> travels to <c>PropPicker.action</c> under.</summary>
    /// <remarks>
    /// Built once and indexed by the enum, which is sound only because the values are contiguous from zero:
    /// a chunk asks for these by the hundred and <see cref="EnumName.Of"/> allocates on every call.
    ///
    /// <para>
    /// Plain strings rather than <c>StringName</c>s, so that touching this class still needs no Godot
    /// runtime - see the note in <c>BestiaClient.Tests.csproj</c>. GDScript converts at the typed boundary
    /// anyway, both on <c>PropPicker.action</c> and on <c>DefaultAction.from_prop_name</c>'s parameter.
    /// </para>
    /// </remarks>
    private static readonly string[] ActionNames =
      Enum.GetValues<PropAction>().Select(action => EnumName.Of(action)).ToArray();

    public static string ActionName(PropAction action) => ActionNames[(int)action];

    /// <summary>One model a scene kind can be drawn as, and the height it is authored at.</summary>
    /// <remarks>
    /// The height travels with the model rather than on the kind because the variants of one kind are not
    /// drawn at one size: the tallest tree model carries its crown nearly twice as high as the shortest, and
    /// each has to be scaled to the server's height on its own measure.
    /// </remarks>
    public readonly struct SceneVariant
    {
      public string Path { get; init; }

      /// <summary>What <see cref="Kind.NaturalHeight"/> is for a mesh kind, for this one model.</summary>
      public float NaturalHeight { get; init; }
    }

    public readonly struct Kind
    {
      /// <summary>The scenes to instantiate per prop, one picked per prop, or null while this kind has none.</summary>
      public SceneVariant[] Scenes { get; init; }

      /// <summary>
      /// What is left of each of <see cref="Scenes"/> once it is removed, index for index, or null for a kind
      /// that leaves no stump.
      /// </summary>
      public SceneVariant[] Stumps { get; init; }

      /// <summary>
      /// The mesh to batch into a multimesh, or null. Ignored when this kind has <see cref="Scenes"/>.
      /// </summary>
      /// <remarks>
      /// A mesh resource rather than a scene, and the difference is what makes the batching possible: a
      /// <c>MultiMesh</c> draws one mesh many times, so a kind that wants this path has to be a single
      /// surface with nothing hanging off it. That is a real constraint - a prop needing two materials or a
      /// child node has to take <see cref="Scenes"/> and pay a draw call each - and it is why the field is
      /// separate rather than the renderer sniffing a scene for a lone <c>MeshInstance3D</c>.
      /// </remarks>
      public string MeshPath { get; init; }

      /// <summary>
      /// The material <see cref="MeshPath"/>'s mesh is drawn with, or null to keep the one it was saved with.
      /// </summary>
      /// <remarks>
      /// Named here rather than left inside the mesh because one material serves several kinds and one mesh
      /// serves several kinds, and the two groupings are not the same: a herb and a blighted herb share a
      /// mesh and differ in colour, while a shrub and a reed share a colour and differ in mesh. Rows carry a
      /// recolour in <see cref="BladeTip"/> and <see cref="BladeBase"/>, which the renderer applies to a
      /// per-kind duplicate, so the file itself stays the one place the look is authored.
      /// </remarks>
      public string MaterialPath { get; init; }

      /// <summary>
      /// The height, in metres, that <see cref="MeshPath"/>'s mesh is authored at. A scene kind carries one
      /// per variant instead - see <see cref="SceneVariant.NaturalHeight"/>.
      /// </summary>
      /// <remarks>
      /// The divisor that turns the server's height into a scale factor, and it has to be measured off the
      /// scene rather than assumed to be one: these are hand-built models placed in the editor, not
      /// unit-height primitives. It also has to be measured at whatever the server's height actually
      /// <i>means</i> for this kind - see the tree's own row.
      /// </remarks>
      public float NaturalHeight { get; init; }

      /// <summary>
      /// Whether <see cref="MeshPath"/>'s mesh was unwrapped with V at 1 on the base and 0 at the tip.
      /// </summary>
      /// <remarks>
      /// A fact about the mesh rather than a look, and it has to be recorded because <c>grass2</c> was
      /// unwrapped the opposite way round from <c>grass</c> and neither is wrong. <c>grass.gdshader</c> reads
      /// it to find which end of a blade is the root, which decides both the colour gradient and which end
      /// the wind bends from - so getting it backwards is visible twice.
      /// </remarks>
      public bool UvVAtTip { get; init; }

      /// <summary>
      /// Tip and base colours for <see cref="MaterialPath"/>'s material, or null to keep its authored ones.
      /// </summary>
      /// <remarks>
      /// The two <c>source_color</c> uniforms <c>grass.gdshader</c> blends along a blade, named <c>color</c>
      /// and <c>color2</c> there. Set on the blighted rows and left alone on the healthy ones, so that
      /// retuning the healthy grass is editing one <c>.tres</c> in the editor and its twins follow.
      /// </remarks>
      public Color? BladeTip { get; init; }

      public Color? BladeBase { get; init; }

      /// <summary>Width of the placeholder box, in metres. Unused once this kind has art.</summary>
      public float PlaceholderWidth { get; init; }

      /// <summary>Colour of the placeholder box. Unused once this kind has a scene.</summary>
      public Color PlaceholderColour { get; init; }

      /// <summary>
      /// How tall a prop of this kind stands, in metres, for the kinds that are also drawn as ordinary
      /// entities.
      /// </summary>
      /// <remarks>
      /// <c>prop-kinds.yml</c>'s collider height, mirrored. Only the kinds a player builds need it: a
      /// generated prop arrives on the static batch, which carries a measured per-entity height, but a
      /// construction site arrives on the entity channel, where a <c>VisualComponent</c> is a kind and an id
      /// and nothing else.
      ///
      /// <para>
      /// Deliberately not <see cref="NaturalHeight"/>, which means something else: that one is the size the
      /// art happens to be drawn at, and <see cref="StaticEntityRenderer"/> divides the wire height by it to
      /// get a scale. Setting it on a placeholder kind would rescale every finished station in the world.
      /// </para>
      /// </remarks>
      public float StructureHeight { get; init; }

      /// <summary>Whether props of this kind get a click target.</summary>
      /// <remarks>
      /// The one field here that mirrors a <i>server</i> rule rather than stating a client one. The authority
      /// is <c>prop-kinds.yml</c>, where a kind is collectible exactly when it has a <c>collect</c> block, and
      /// the server refuses anything else.
      ///
      /// <para>
      /// Mirrored anyway because the client has to decide whether to build a collision shape before it could
      /// possibly ask, and the alternatives are worse: a per-entry flag on the wire pays for 13 bits of
      /// per-<i>kind</i> information on every one of the thousands of entries in a view volume, and a
      /// catalogue handshake is a second answer to a question <c>WorldInfoSMSG</c>'s version already answers.
      /// </para>
      ///
      /// <para>
      /// Skew is fail-safe both ways. A wrong <c>true</c> costs one refused click and a
      /// <c>COLLECT_NOT_COLLECTIBLE</c> that names the drift - which is why that code is kept distinct from
      /// <c>COLLECT_TARGET_GONE</c>. A missing <c>true</c> just makes the prop unclickable, the same quiet
      /// absence this table already accepts for a kind with no art.
      /// </para>
      /// </remarks>
      public bool Collectible { get; init; }

      /// <summary>What a click on props of this kind is for, and so which cursor names it.</summary>
      /// <remarks>
      /// Purely the client's opinion, unlike <see cref="Collectible"/>: it is what the player is told the
      /// click will do, and <c>MouseStateDefault</c> is what sends the matching request. A kind with
      /// <see cref="PropAction.None"/> is scenery and gets no click target at all.
      /// </remarks>
      public PropAction Action { get; init; }

      public bool HasScene => Scenes is { Length: > 0 };

      /// <summary>The model a prop with the wire <paramref name="variant"/> is drawn as. Only meaningful when <see cref="HasScene"/>.</summary>
      /// <remarks>
      /// Off the server's variant, not the entity id: the server mints a new entity id every time a chunk
      /// streams back in, while the variant comes from the prop's durable name. So a tree keeps its model, and
      /// a stump can match the tree it was cut from.
      /// </remarks>
      public SceneVariant SceneFor(int variant) => Scenes[variant % Scenes.Length];

      public bool HasStumps => Stumps is { Length: > 0 };

      /// <summary>The stump left by the model <see cref="SceneFor"/> picks for the same <paramref name="variant"/>.</summary>
      public SceneVariant StumpFor(int variant) => Stumps[variant % Stumps.Length];

      public bool HasMesh => !string.IsNullOrEmpty(MeshPath);
    }

    /// <summary>The grass mesh the shrubs and reeds are drawn with, and the material for it.</summary>
    /// <remarks>
    /// A clump of a dozen blades, 2.24 m tall and 2.85 m across. Used at its own size and scaled by the prop's
    /// height, because the server's kinds differ in habit and not only in height: a reed bed is a thicket where
    /// a herb is one plant you could pick.
    ///
    /// <para>
    /// The other mesh in that folder, <c>grass2</c>'s single tuft, was what the herbs used and is now
    /// <see cref="TerrainGrass"/>'s alone - see the note on the herb rows below.
    /// </para>
    /// </remarks>
    private const string GrassClump = "res://Game/Terrain/Grass/grass.res";

    private const string GrassMaterial = "res://Game/Terrain/Grass/grass.tres";

    private const string TreeDir = "res://Game/Terrain/Trees/";

    private const string RockDir = "res://Game/Terrain/Rocks/";

    private const string LanternScene = "res://Game/World/Fixtures/Lantern/Lantern.tscn";

    /// <summary>The ten tree models, or with <paramref name="prefix"/> <c>Stump_</c> the stumps cut from them.</summary>
    /// <remarks>
    /// A stump is its tree cut down in the same model space, so it scales by the same natural height and its
    /// trunk is as wide as the tree that stood there.
    /// </remarks>
    private static SceneVariant[] TreeModels(string prefix) => new[]
    {
      new SceneVariant { Path = TreeDir + prefix + "CommonTree_1.gltf", NaturalHeight = 4.6843f },
      new SceneVariant { Path = TreeDir + prefix + "CommonTree_2.gltf", NaturalHeight = 5.1433f },
      new SceneVariant { Path = TreeDir + prefix + "CommonTree_3.gltf", NaturalHeight = 5.8794f },
      new SceneVariant { Path = TreeDir + prefix + "CommonTree_4.gltf", NaturalHeight = 7.5021f },
      new SceneVariant { Path = TreeDir + prefix + "CommonTree_5.gltf", NaturalHeight = 4.5600f },
      new SceneVariant { Path = TreeDir + prefix + "Pine_1.gltf", NaturalHeight = 4.6203f },
      new SceneVariant { Path = TreeDir + prefix + "Pine_2.gltf", NaturalHeight = 4.1294f },
      new SceneVariant { Path = TreeDir + prefix + "Pine_3.gltf", NaturalHeight = 5.1723f },
      new SceneVariant { Path = TreeDir + prefix + "Pine_4.gltf", NaturalHeight = 6.2203f },
      new SceneVariant { Path = TreeDir + prefix + "Pine_5.gltf", NaturalHeight = 4.9712f },
    };

    /// <summary>
    /// Every kind the server can send, in <c>StaticEntityKind</c> ordinal order.
    /// </summary>
    /// <remarks>
    /// The placeholder colours are chosen to be told apart at a glance and to agree with the material a prop
    /// is made of where the player has already learned one: the aetherite shards carry the ore block's violet
    /// rather than the mana crystals' blue, because recognising them as a sign of the rock below is the whole
    /// point of that prop. The landmarks are pale worked-stone greys, since everything above them is a growth
    /// or an ore and reads as coloured.
    /// </remarks>
    private static readonly Kind[] Kinds =
    {
      // TREE. The one kind with real art: Quaternius' common trees and pines, one picked per tree. Each
      // natural height is the y of that model's canopy centre - the middle of its leaf surface's vertical
      // extent - *not* the model's full height, because the server's height for a tree is "clear bole plus
      // crown centre" (see VegetationParams.minTrunkHeight). Dividing by the full height would shrink every
      // tree and put its crown below where the generator did.
      new Kind
      {
        Scenes = TreeModels(""),
        Stumps = TreeModels("Stump_"),
        Action = PropAction.Chop
      },

      // BLIGHTED_TREE. Deliberately still a box rather than the tree scene above: a corrupted tree drawn
      // identically to a healthy one is worse than one that is obviously unfinished, because the corruption
      // boundary is a thing the player is meant to read off the landscape.
      new Kind
      {
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.30f, 0.26f, 0.20f), Action = PropAction.Chop
      },

      // MANA_CRYSTAL_SMALL / _LARGE. Collectible: picked up with a click rather than felled. Mine rather
      // than Collect only so that the cursor reads as ore - the request the click sends is the same one.
      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.35f, 0.55f, 0.85f), Collectible = true,
        Action = PropAction.Mine
      },
      new Kind
      {
        PlaceholderWidth = 0.5f, PlaceholderColour = new Color(0.45f, 0.35f, 0.85f), Collectible = true,
        Action = PropAction.Mine
      },

      // WOUND_SPIRE.
      new Kind { PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.75f, 0.20f, 0.70f) },

      // AETHERITE_SHARD_SMALL / _LARGE. Squat and wide, unlike a crystal. Also collectible.
      new Kind
      {
        PlaceholderWidth = 0.7f, PlaceholderColour = new Color(0.42f, 0.33f, 0.52f), Collectible = true,
        Action = PropAction.Mine
      },
      new Kind
      {
        PlaceholderWidth = 0.9f, PlaceholderColour = new Color(0.58f, 0.40f, 0.78f), Collectible = true,
        Action = PropAction.Mine
      },

      // The six points of interest, each a distinct width: at most one of each per world, so they have to be
      // told apart on sight rather than by comparison.
      new Kind { PlaceholderWidth = 1.2f, PlaceholderColour = new Color(0.55f, 0.52f, 0.48f) }, // POI_LOST_GRAVE
      new Kind { PlaceholderWidth = 2.4f, PlaceholderColour = new Color(0.62f, 0.60f, 0.58f) }, // POI_STANDING_STONES
      new Kind { PlaceholderWidth = 0.8f, PlaceholderColour = new Color(0.72f, 0.70f, 0.66f) }, // POI_BROKEN_OBELISK
      new Kind { PlaceholderWidth = 0.5f, PlaceholderColour = new Color(0.66f, 0.62f, 0.56f) }, // POI_WAYSTONE
      new Kind { PlaceholderWidth = 0.9f, PlaceholderColour = new Color(0.48f, 0.42f, 0.38f) }, // POI_PETRIFIED_TREE
      new Kind { PlaceholderWidth = 0.7f, PlaceholderColour = new Color(0.40f, 0.44f, 0.40f) }, // POI_SUNKEN_IDOL

      // The nine buildings a town is made of.
      //
      // <b><see cref="Kind.PlaceholderWidth"/> is ignored for every one of them</b>, and that is the only place
      // in this table where a field does not apply. A building's footprint arrives per entry on the wire -
      // `half_length_dm` and `half_width_dm` - because a temple and a barn are not the same size and no
      // per-kind number could stand in for the lot each was cut from. The widths below are left at a plausible
      // value rather than zero so that a regression which drops the wire extents draws something rather than
      // nothing; <see cref="StaticEntityRenderer"/> is where the choice is made.
      //
      // Colours are the materials these used to be built from, back when a building was voxels: the tan of
      // timber and plaster for the ordinary ones, worked-stone grey for the civic and religious ones that were
      // masonry more often than not. A player who learned the old palette reads the same town.
      // Gilded so a newcomer can find it across the square.
      new Kind
      {
        PlaceholderWidth = 6.0f, PlaceholderColour = new Color(0.80f, 0.64f, 0.26f), StructureHeight = 4f,
        Action = PropAction.Use
      }, // AUCTION_HOUSE
      new Kind { PlaceholderWidth = 6.0f, PlaceholderColour = new Color(0.74f, 0.72f, 0.66f) }, // BUILDING_TEMPLE
      new Kind { PlaceholderWidth = 6.0f, PlaceholderColour = new Color(0.66f, 0.65f, 0.62f) }, // BUILDING_CIVIC
      new Kind { PlaceholderWidth = 4.5f, PlaceholderColour = new Color(0.72f, 0.62f, 0.44f) }, // BUILDING_SHOP
      new Kind { PlaceholderWidth = 4.5f, PlaceholderColour = new Color(0.58f, 0.46f, 0.32f) }, // BUILDING_CRAFT
      new Kind { PlaceholderWidth = 5.0f, PlaceholderColour = new Color(0.50f, 0.42f, 0.32f) }, // BUILDING_WAREHOUSE
      new Kind { PlaceholderWidth = 5.0f, PlaceholderColour = new Color(0.70f, 0.56f, 0.36f) }, // BUILDING_INN
      new Kind { PlaceholderWidth = 4.5f, PlaceholderColour = new Color(0.78f, 0.74f, 0.64f) }, // BUILDING_RESIDENCE
      new Kind { PlaceholderWidth = 5.0f, PlaceholderColour = new Color(0.64f, 0.54f, 0.34f) }, // BUILDING_FARM

      // The three crafting stations a player builds, and the first rows here for kinds no generator produces.
      // Boxes for now, and deliberately small ones: a station is a thing you stand next to rather than a
      // landmark, and drawing it the size of a shed would make a workbench read as a building.
      //
      // Not Collectible - a station is taken down by damaging it, not picked up by a passer-by, and
      // prop-kinds.yml gives none of them a `collect` block - but PropAction.Use, because clicking one is how
      // you use it. `InteractEntityCMSG` is what that click sends.
      // StructureHeight, unlike every row above, because these are the kinds a player builds - so each is
      // also drawn as an entity while it is still a construction site, where no wire height is sent.
      new Kind
      {
        PlaceholderWidth = 1.2f, PlaceholderColour = new Color(0.55f, 0.40f, 0.24f), StructureHeight = 1f,
        Action = PropAction.Use
      }, // WORKBENCH
      new Kind
      {
        PlaceholderWidth = 1.4f, PlaceholderColour = new Color(0.42f, 0.36f, 0.34f), StructureHeight = 2f,
        Action = PropAction.Use
      }, // FURNACE
      new Kind
      {
        PlaceholderWidth = 1.8f, PlaceholderColour = new Color(0.36f, 0.30f, 0.30f), StructureHeight = 2f,
        Action = PropAction.Use
      }, // FORGE

      // The ground cover: a herb, a shrub and a reed, each with its blighted twin. Collectible - every one of
      // them has a `collect` block in prop-kinds.yml - so all six get a click target.
      //
      // The first kinds to take the batched-mesh path, and the reason it exists. These are the densest props
      // in the world: `GroundCoverParams.litterGain` is set at about twice the tree density, so a view volume
      // holds on the order of a thousand of them. A scene instance each would be a thousand draw calls for
      // the layer the player is least likely to be looking at.
      //
      // The natural heights are the top of each mesh's own AABB above y=0, which is where they stand - not
      // the AABB's height, which counts the few millimetres both dip below the origin so that a base does not
      // float over uneven ground.
      //
      // `PlaceholderWidth` is still read, and only for the pick box's floor: the drawn footprint is the mesh
      // scaled, which is far wider than these numbers, and `StaticEntityRenderer` takes the larger.
      //
      // The blighted shrubs and reeds are their healthy twin's mesh recoloured, unlike the blighted *tree*
      // which is still a box on the grounds that a corrupted tree drawn as a healthy one is worse than one
      // that is obviously unfinished. That argument does not carry down here: a blighted shrub desaturated
      // toward the corruption palette is not mistakable for a healthy one at the size these are drawn, and a
      // thicket of magenta-free placeholder boxes in the middle of real grass would be the worse read.
      //
      // The **herbs** are the exception, and deliberately so. They were drawn from `grass2`, which
      // `TerrainGrass` now scatters across the whole decorative field - so a herb keeping that art would be
      // one 0.45 m tuft to be picked out of a field of near-identical ones. Being obviously not scenery is
      // worth more than being pretty for the one kind here the player has to *find*, so both herbs are back
      // to boxes until they have a model of their own.
      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.42f, 0.62f, 0.28f), Collectible = true,
        Action = PropAction.Collect
      }, // HERB
      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.44f, 0.44f, 0.26f), Collectible = true,
        Action = PropAction.Collect
      }, // BLIGHTED_HERB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.28f, 0.46f, 0.24f), Collectible = true,
        Action = PropAction.Collect
      }, // SHRUB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        BladeTip = new Color(0.34f, 0.32f, 0.22f), BladeBase = new Color(0.22f, 0.19f, 0.11f),
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.34f, 0.32f, 0.22f), Collectible = true,
        Action = PropAction.Collect
      }, // BLIGHTED_SHRUB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.56f, 0.60f, 0.34f), Collectible = true,
        Action = PropAction.Collect
      }, // REED
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        BladeTip = new Color(0.46f, 0.44f, 0.30f), BladeBase = new Color(0.28f, 0.24f, 0.13f),
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.46f, 0.44f, 0.30f), Collectible = true,
        Action = PropAction.Collect
      }, // BLIGHTED_REED

      // The ward stone at each home village. Drawn as an entity like a construction site, so it needs a
      // StructureHeight; no Action, because nothing is done to it with a click.
      new Kind
      {
        PlaceholderWidth = 1.0f, PlaceholderColour = new Color(0.62f, 0.70f, 0.82f), StructureHeight = 4f
      },  // WARD_STONE

      // A guild's War Banner, an entity like the ward stone: a tall, thin pole in banner red until it has a model.
      new Kind
      {
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.78f, 0.16f, 0.16f), StructureHeight = 3f
      }, // WAR_BANNER

      // What lies on the ground (worldgen's LootScatter). A boulder is one size drawn as one of Quaternius'
      // three medium rocks, picked per boulder like a tree's model; each natural height is that model's top
      // above its origin. Broken rather than picked, so it gets Break and no Collectible.
      new Kind
      {
        Scenes = new[]
        {
          new SceneVariant { Path = RockDir + "Rock_Medium_1.gltf", NaturalHeight = 1.989f },
          new SceneVariant { Path = RockDir + "Rock_Medium_2.gltf", NaturalHeight = 1.848f },
          new SceneVariant { Path = RockDir + "Rock_Medium_3.gltf", NaturalHeight = 2.001f },
        },
        PlaceholderWidth = 1.8f, PlaceholderColour = new Color(0.52f, 0.50f, 0.47f), Action = PropAction.Break
      }, // BOULDER

      // A loose stone: always the same pebble, because it is loot rather than scenery. Collectible, from its
      // `collect` block in prop-kinds.yml.
      new Kind
      {
        Scenes = new[] { new SceneVariant { Path = RockDir + "Pebble_Round_3.gltf", NaturalHeight = 0.097f } },
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.60f, 0.58f, 0.54f), Collectible = true,
        Action = PropAction.Collect
      }, // LOOSE_STONE

      // A fallen stick. A long, low box in bark brown until it has a model: the free MegaKit has none.
      new Kind
      {
        PlaceholderWidth = 0.7f, PlaceholderColour = new Color(0.40f, 0.29f, 0.18f), Collectible = true,
        Action = PropAction.Collect
      }, // STICK

      // The set dressing - worldgen's FixtureKind, one row per entry in its order. Boxes until there is art, but
      // boxes of the right footprint: a fence run, a pier, a crop tile and an inn all send their half-extents,
      // so each draws as a slab the length and width of the thing. Placeholder widths are for the point-sized
      // ones (a well, a signpost). The lantern is the one real scene, because its light is the point of it; the
      // outcrops reuse the boulders' rocks, scaled up.
      //
      // The four readable kinds take Use, which sends the interact the server reads them on.
      new Kind { PlaceholderWidth = 3.0f, PlaceholderColour = new Color(0.50f, 0.38f, 0.24f) }, // FIXTURE_PIER
      new Kind { PlaceholderWidth = 1.8f, PlaceholderColour = new Color(0.44f, 0.30f, 0.18f) }, // FIXTURE_BOAT
      new Kind { PlaceholderWidth = 0.2f, PlaceholderColour = new Color(0.46f, 0.36f, 0.24f) }, // FIXTURE_FENCE
      new Kind { PlaceholderWidth = 4.0f, PlaceholderColour = new Color(0.78f, 0.68f, 0.30f) }, // FIXTURE_CROP
      new Kind { PlaceholderWidth = 2.6f, PlaceholderColour = new Color(0.84f, 0.72f, 0.36f) }, // FIXTURE_HAYSTACK
      new Kind { PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.55f, 0.42f, 0.26f) }, // FIXTURE_SCARECROW
      new Kind { PlaceholderWidth = 5.0f, PlaceholderColour = new Color(0.80f, 0.76f, 0.66f) }, // FIXTURE_WINDMILL
      new Kind { PlaceholderWidth = 1.8f, PlaceholderColour = new Color(0.56f, 0.55f, 0.52f) }, // FIXTURE_WELL
      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.52f, 0.40f, 0.26f), Action = PropAction.Use
      }, // FIXTURE_SIGNPOST
      new Kind
      {
        PlaceholderWidth = 0.5f, PlaceholderColour = new Color(0.66f, 0.64f, 0.60f), Action = PropAction.Use
      }, // FIXTURE_MILESTONE
      new Kind
      {
        Scenes = new[] { new SceneVariant { Path = LanternScene, NaturalHeight = 2.8f } },
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.35f, 0.95f, 0.90f)
      }, // FIXTURE_LANTERN
      new Kind { PlaceholderWidth = 1.2f, PlaceholderColour = new Color(0.58f, 0.57f, 0.54f) }, // FIXTURE_CAIRN
      new Kind
      {
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.62f, 0.62f, 0.60f), Action = PropAction.Use
      }, // FIXTURE_GRAVESTONE
      new Kind
      {
        PlaceholderWidth = 0.8f, PlaceholderColour = new Color(0.74f, 0.73f, 0.70f), Action = PropAction.Use
      }, // FIXTURE_NAMED_GRAVE
      new Kind
      {
        Scenes = new[]
        {
          new SceneVariant { Path = RockDir + "Rock_Medium_1.gltf", NaturalHeight = 1.989f },
          new SceneVariant { Path = RockDir + "Rock_Medium_2.gltf", NaturalHeight = 1.848f },
          new SceneVariant { Path = RockDir + "Rock_Medium_3.gltf", NaturalHeight = 2.001f },
        },
        PlaceholderWidth = 3.0f, PlaceholderColour = new Color(0.62f, 0.44f, 0.32f)
      }, // FIXTURE_OUTCROP
      new Kind { PlaceholderWidth = 5.0f, PlaceholderColour = new Color(0.70f, 0.56f, 0.36f) }, // FIXTURE_INN

      // Building pieces, drawn from the cells they block - see StaticEntityRenderer.AddStructurePiece.
      new Kind { PlaceholderWidth = 1.0f, PlaceholderColour = new Color(0.62f, 0.50f, 0.36f) }, // STRUCTURE_WALL
      new Kind { PlaceholderWidth = 1.0f, PlaceholderColour = new Color(0.55f, 0.42f, 0.28f) }, // STRUCTURE_FLOOR
      new Kind { PlaceholderWidth = 1.0f, PlaceholderColour = new Color(0.48f, 0.38f, 0.26f) }, // STRUCTURE_STAIRS

      // Cold and lit, in the order of `Campfire.COLD_VARIANT` and `LIT_VARIANT` on the server.
      new Kind
      {
        Scenes = new[]
        {
          new SceneVariant { Path = "res://Game/World/Campfire/CampfireCold.tscn", NaturalHeight = 0.3f },
          new SceneVariant { Path = "res://Game/World/Campfire/CampfireLit.tscn", NaturalHeight = 0.7f },
        },
        PlaceholderWidth = 0.8f, PlaceholderColour = new Color(0.62f, 0.30f, 0.16f), StructureHeight = 0.5f,
        Action = PropAction.Use
      }, // CAMPFIRE

      // A lump of resin under the conifers, ground loot like the stick: a small amber box until it has a model.
      new Kind
      {
        PlaceholderWidth = 0.25f, PlaceholderColour = new Color(0.85f, 0.55f, 0.15f), Collectible = true,
        Action = PropAction.Collect
      }, // RESIN

      // A ring of glowing paste around a focus stone, where a ritual is held: a flat violet disc until it has a model.
      new Kind
      {
        PlaceholderWidth = 3.0f, PlaceholderColour = new Color(0.55f, 0.35f, 0.95f), StructureHeight = 0.1f,
        Action = PropAction.Use
      }, // MANA_CIRCLE

      // A player's chest: a small dark box until it has a model. PropAction.Use, because clicking it opens it.
      new Kind
      {
        PlaceholderWidth = 0.9f, PlaceholderColour = new Color(0.40f, 0.27f, 0.14f), StructureHeight = 0.7f,
        Action = PropAction.Use
      }, // CHEST

      // A sighting scope on a tripod, where Cartography surveys from: a thin brass post until it has a model.
      new Kind
      {
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.78f, 0.62f, 0.30f), StructureHeight = 1.5f,
        Action = PropAction.Use
      }, // THEODOLITE

      // The rare herbs, boxes like the wild herb until they have models. Each has its own colour, because the point
      // of a rare herb is to be told apart from a wild one at a glance.
      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.20f, 0.55f, 0.30f), Collectible = true,
        Action = PropAction.Collect
      }, // MEDICINAL_HERB

      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.35f, 0.45f, 0.95f), Collectible = true,
        Action = PropAction.Collect
      }, // MANA_HERB

      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.70f, 0.88f, 0.95f), Collectible = true,
        Action = PropAction.Collect
      }, // FROST_HERB

      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.95f, 0.75f, 0.20f), Collectible = true,
        Action = PropAction.Collect
      }, // SUN_HERB

      new Kind
      {
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.85f, 0.85f, 0.90f), Collectible = true,
        Action = PropAction.Collect
      }, // GHOST_HERB

      // A Trader's street stall, an entity like the War Banner: a low cloth-coloured counter until it has a model.
      new Kind
      {
        PlaceholderWidth = 1.5f, PlaceholderColour = new Color(0.72f, 0.36f, 0.30f), StructureHeight = 1f,
        Action = PropAction.Use
      }, // STREET_STALL

      // A player town's sign, an entity like the ward stone. Clicked to open the town's window.
      new Kind
      {
        PlaceholderWidth = 0.5f, PlaceholderColour = new Color(0.66f, 0.50f, 0.30f), StructureHeight = 3f,
        Action = PropAction.Use
      }, // CITY_SIGN

      // A post office players built in their town. A plain civic box until it has a model; StructureHeight for
      // its construction site, like a station.
      new Kind
      {
        PlaceholderWidth = 7.0f, PlaceholderColour = new Color(0.70f, 0.66f, 0.58f), StructureHeight = 4f,
        Action = PropAction.Use
      }, // POST_OFFICE

      // An abandoned camp beside a road: the cold campfire, with nothing to light.
      new Kind
      {
        Scenes = new[] { new SceneVariant { Path = "res://Game/World/Campfire/CampfireCold.tscn", NaturalHeight = 0.3f } },
        PlaceholderWidth = 0.8f, PlaceholderColour = new Color(0.40f, 0.36f, 0.32f)
      }, // FIXTURE_CAMP

      // A pen of planks and reed where a pair of bestias breeds. Clicking it opens its window.
      new Kind
      {
        PlaceholderWidth = 3.0f, PlaceholderColour = new Color(0.66f, 0.56f, 0.38f), StructureHeight = 1f,
        Action = PropAction.Use
      }, // BREEDING_FARM

      // A brick hearth with a nest on top, where one egg hatches. Clicking it opens its window.
      new Kind
      {
        PlaceholderWidth = 1.2f, PlaceholderColour = new Color(0.64f, 0.38f, 0.28f), StructureHeight = 1f,
        Action = PropAction.Use
      }, // INCUBATOR

      // A mining shaft: a tall timber frame until it has a model. Nothing to open; its bestias work it.
      new Kind
      {
        PlaceholderWidth = 2.5f, PlaceholderColour = new Color(0.33f, 0.25f, 0.17f), StructureHeight = 3f
      } // MINING_SHAFT
    };

    /// <summary>
    /// A kind the server sent that this client has no row for.
    /// </summary>
    /// <remarks>
    /// Magenta, and drawn rather than skipped. A server one kind ahead of this client is a version skew that
    /// should be obvious in the world instead of being a quiet absence indistinguishable from ground that
    /// genuinely has nothing on it.
    ///
    /// <para>
    /// Not <see cref="Kind.Collectible"/>, though. Drawing a kind we do not understand is honest; offering a
    /// click on one is a guess, and the server would refuse it anyway.
    /// </para>
    /// </remarks>
    private static readonly Kind Unknown =
      new() { PlaceholderWidth = 0.5f, PlaceholderColour = new Color(1f, 0f, 1f) };

    public static Kind Of(int kind) => (uint)kind < (uint)Kinds.Length ? Kinds[kind] : Unknown;
  }
}
