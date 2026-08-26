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
  /// A kind with a <see cref="Kind.ScenePath"/> is instantiated per prop, so it can be a real model with a
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
    public readonly struct Kind
    {
      /// <summary>The scene to instantiate per prop, or null while this kind has no art.</summary>
      public string ScenePath { get; init; }

      /// <summary>
      /// The mesh to batch into a multimesh, or null. Ignored when this kind has a <see cref="ScenePath"/>.
      /// </summary>
      /// <remarks>
      /// A mesh resource rather than a scene, and the difference is what makes the batching possible: a
      /// <c>MultiMesh</c> draws one mesh many times, so a kind that wants this path has to be a single
      /// surface with nothing hanging off it. That is a real constraint - a prop needing two materials or a
      /// child node has to take <see cref="ScenePath"/> and pay a draw call each - and it is why the field is
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
      /// The height, in metres, that <see cref="ScenePath"/>'s scene or <see cref="MeshPath"/>'s mesh is
      /// authored at.
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

      public bool HasScene => !string.IsNullOrEmpty(ScenePath);

      public bool HasMesh => !string.IsNullOrEmpty(MeshPath);
    }

    /// <summary>The two grass meshes, and the material they are both drawn with.</summary>
    /// <remarks>
    /// <c>grass</c> is a clump of a dozen blades 2.24 m tall and 2.85 m across; <c>grass2</c> is a single
    /// tuft 0.71 m tall. They are used at their own sizes rather than one being a scaled copy of the other,
    /// because the server's kinds differ in habit and not only in height: a herb is one plant you could pick
    /// and a reed bed is a thicket, and scaling the thicket down to 0.45 m draws a herb as twelve blades.
    /// </remarks>
    private const string GrassClump = "res://Game/Terrain/Grass/grass.res";

    private const string GrassTuft = "res://Game/Terrain/Grass/grass2.res";

    private const string GrassMaterial = "res://Game/Terrain/Grass/grass.tres";

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
      // TREE. The one kind with real art. Its natural height is the y of the canopy centre in TreeVisual.tscn
      // (trunk node at 3.5063 plus canopy at 3.9816 in trunk-local space), *not* the trunk mesh's own 8 m
      // length, because the server's height for a tree is "clear bole plus crown centre" - see
      // VegetationParams.minTrunkHeight. Dividing by the trunk length instead would grow every tree by about
      // a tenth and put its crown where the generator did not.
      new Kind { ScenePath = "res://Game/Entity/Visual/TreeVisual/TreeVisual.tscn", NaturalHeight = 7.4878f },

      // BLIGHTED_TREE. Deliberately still a box rather than the tree scene above: a corrupted tree drawn
      // identically to a healthy one is worse than one that is obviously unfinished, because the corruption
      // boundary is a thing the player is meant to read off the landscape.
      new Kind { PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.30f, 0.26f, 0.20f) },

      // MANA_CRYSTAL_SMALL / _LARGE. Collectible: picked up with a click rather than felled.
      new Kind { PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.35f, 0.55f, 0.85f), Collectible = true },
      new Kind { PlaceholderWidth = 0.5f, PlaceholderColour = new Color(0.45f, 0.35f, 0.85f), Collectible = true },

      // WOUND_SPIRE.
      new Kind { PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.75f, 0.20f, 0.70f) },

      // AETHERITE_SHARD_SMALL / _LARGE. Squat and wide, unlike a crystal. Also collectible.
      new Kind { PlaceholderWidth = 0.7f, PlaceholderColour = new Color(0.42f, 0.33f, 0.52f), Collectible = true },
      new Kind { PlaceholderWidth = 0.9f, PlaceholderColour = new Color(0.58f, 0.40f, 0.78f), Collectible = true },

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
      new Kind { PlaceholderWidth = 6.0f, PlaceholderColour = new Color(0.62f, 0.60f, 0.56f) }, // BUILDING_MARKET
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
      // Not Collectible: a station is taken down by damaging it, not picked up by a passer-by, and
      // prop-kinds.yml gives none of them a `collect` block - so offering the click would only earn a
      // COLLECT_NOT_COLLECTIBLE.
      new Kind { PlaceholderWidth = 1.2f, PlaceholderColour = new Color(0.55f, 0.40f, 0.24f) }, // WORKBENCH
      new Kind { PlaceholderWidth = 1.4f, PlaceholderColour = new Color(0.42f, 0.36f, 0.34f) }, // FURNACE
      new Kind { PlaceholderWidth = 1.8f, PlaceholderColour = new Color(0.36f, 0.30f, 0.30f) }, // FORGE

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
      // The blighted twins are the same meshes recoloured, unlike the blighted *tree* which is still a box on
      // the grounds that a corrupted tree drawn as a healthy one is worse than one that is obviously
      // unfinished. That argument does not carry down here: a blighted herb desaturated toward the corruption
      // palette is not mistakable for a healthy one at the size these are drawn, and a meadow of magenta-free
      // placeholder boxes in the middle of real grass would be the worse read.
      new Kind
      {
        MeshPath = GrassTuft, MaterialPath = GrassMaterial, NaturalHeight = 0.7053f, UvVAtTip = true,
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.42f, 0.62f, 0.28f), Collectible = true
      }, // HERB
      new Kind
      {
        MeshPath = GrassTuft, MaterialPath = GrassMaterial, NaturalHeight = 0.7053f, UvVAtTip = true,
        BladeTip = new Color(0.44f, 0.44f, 0.26f), BladeBase = new Color(0.26f, 0.23f, 0.10f),
        PlaceholderWidth = 0.3f, PlaceholderColour = new Color(0.44f, 0.44f, 0.26f), Collectible = true
      }, // BLIGHTED_HERB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.28f, 0.46f, 0.24f), Collectible = true
      }, // SHRUB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        BladeTip = new Color(0.34f, 0.32f, 0.22f), BladeBase = new Color(0.22f, 0.19f, 0.11f),
        PlaceholderWidth = 0.6f, PlaceholderColour = new Color(0.34f, 0.32f, 0.22f), Collectible = true
      }, // BLIGHTED_SHRUB
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.56f, 0.60f, 0.34f), Collectible = true
      }, // REED
      new Kind
      {
        MeshPath = GrassClump, MaterialPath = GrassMaterial, NaturalHeight = 2.2391f,
        BladeTip = new Color(0.46f, 0.44f, 0.30f), BladeBase = new Color(0.28f, 0.24f, 0.13f),
        PlaceholderWidth = 0.4f, PlaceholderColour = new Color(0.46f, 0.44f, 0.30f), Collectible = true
      }  // BLIGHTED_REED
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
