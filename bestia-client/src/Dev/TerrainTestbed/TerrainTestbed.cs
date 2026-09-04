using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Dev
{
  /// <summary>
  /// A static patch of terrain containing every texture slot at once, meshed in the editor so the terrain
  /// shader can be tuned without a server.
  /// </summary>
  /// <remarks>
  /// <b>It runs the shipping mesher, not a stand-in.</b> The field is built as real
  /// <see cref="VoxelChunk"/>s and handed to <see cref="SurfaceNets"/> through <see cref="IChunkSource"/> -
  /// the same interface the client's own store implements - so the vertices, the packed slot weights and the
  /// vertex tints are bit-for-bit what the game would upload for the same voxels. A hand-built quad grid
  /// would have been a tenth of the code and would have let the two drift, which is precisely the drift a
  /// testbed is supposed to catch.
  ///
  /// <para>
  /// <b>The one thing it cannot borrow is the material.</b> <see cref="TerrainMaterials.Load"/> assigns the
  /// assembled texture arrays onto <c>terrain.tres</c> itself, which is correct at runtime and unsafe in the
  /// editor: a <c>Texture2DArray</c> built in memory has no path, so saving the resource afterwards writes it
  /// into the file as <c>_images = Array[Image]([null, null, ...])</c> - a permanently empty array committed
  /// to the shipping material. So this scene carries its own <c>terrain_testbed.tres</c> pointing at the same
  /// shader, and <see cref="CopyTuningToShippingMaterial"/> moves the numbers across when they are worth
  /// keeping. Textures are never copied, which is what makes that direction safe.
  /// </para>
  ///
  /// <para>
  /// Everything it builds is added <b>without an owner</b>, so none of it is serialised into the scene. That
  /// keeps a few megabytes of <c>ArrayMesh</c> out of git and makes the scene file a description of the
  /// settings rather than of the result. The cost is that the generated nodes do not appear in the scene tree
  /// dock - select this node instead, the material is on it.
  /// </para>
  ///
  /// <para>
  /// <b>Scorch (slot 8) is reachable here and nowhere else.</b> The server sends a scorch mask
  /// (<c>ChunkGroundOverlaySMSG</c>) and <c>ChunkStreamManager</c> stores it, but nothing substitutes it into
  /// the mesher's weights yet - so in the running game no vertex has ever carried slot 8, and slots 9 to 15
  /// have no block at all. <see cref="FieldPalette.Slots"/> invents one material per slot precisely so that
  /// art for them can be judged before the code that would emit them exists.
  /// </para>
  /// </remarks>
  [Tool]
  public partial class TerrainTestbed : Node3D
  {
    /// <summary>What the field is made of.</summary>
    public enum FieldPalette
    {
      /// <summary>One invented material per texture slot, all sixteen, in ordinal order.</summary>
      Slots,

      /// <summary>Real blocks from the shipping palette, chosen to put several tints on one slot.</summary>
      Materials
    }

    /// <summary>What shape the field is.</summary>
    public enum FieldRelief
    {
      /// <summary>A flat plane. Every boundary is read without slope in the way.</summary>
      Flat,

      /// <summary>A dome per patch, so each material is seen from flat through to steeper than
      /// <c>cliff_end</c>.</summary>
      Domes
    }

    /// <summary>What a column is made of below its surface.</summary>
    public enum FieldColumn
    {
      /// <summary>
      /// The patch's own material all the way down.
      /// </summary>
      /// <remarks>
      /// The default, and the right one for judging art: nothing but the material under test is within reach of
      /// any vertex, including on a steep flank where a cover would put a second material beside every one of
      /// them.
      /// </remarks>
      Uniform,

      /// <summary>
      /// One voxel of the patch's material over subsoil over bedrock, the way the generator writes a column.
      /// </summary>
      /// <remarks>
      /// <b>The acceptance criterion is that this looks the same as <see cref="Uniform"/>.</b> A surface cap is
      /// one voxel thick and is the only partial voxel in a column, so a mesher that ranks cells by how much
      /// material they hold names the subsoil instead of the cap and paints the whole field brown - which is
      /// what the running game looked like while the testbed, uniform to the bottom, looked correct. Any patch
      /// that goes brown or grey here and not under <see cref="Uniform"/> is that bug, back again. The one
      /// difference that is meant to survive is a vertical face deep enough to expose the subsoil honestly.
      /// </remarks>
      Layered
    }

    /// <summary>Patches per axis. Sixteen slots want four.</summary>
    private const int Columns = 4;

    private const int Rows = 4;

    /// <summary>The world's own chunk size, so the seam logic is exercised rather than sidestepped.</summary>
    private const int ChunkSize = 32;

    /// <summary>
    /// Voxels per chunk column here, against the world's 256.
    /// </summary>
    /// <remarks>
    /// Shallower only to keep a rebuild instant. Nothing in the mesher scales with it - <see cref="ChunkBands"/>
    /// reduces a column to its active band before <see cref="TerrainPatch"/> gathers anything - so the height is
    /// a memory figure and not a fidelity one.
    /// </remarks>
    private const int ChunkHeight = 64;

    /// <summary>Metres per voxel, matching the generator's <c>WorldConfig.voxelSize</c>.</summary>
    private const float VoxelSize = 1.0f;

    /// <summary>Where the plain sits, in voxels above sea level. Clear of the chunk floor and of its ceiling.</summary>
    private const int BaseElevation = 12;

    /// <summary>
    /// Which block stands for each slot in <see cref="FieldPalette.Slots"/>.
    /// </summary>
    /// <remarks>
    /// Ids rather than colours, so the tints come out of <c>BlockAppearance.Palette</c> and follow it when it is
    /// edited. The table stops at eight because no block maps past <c>Wetland</c> yet; those slots get a flat
    /// grey, which is the honest answer for a material nobody has decided the colour of.
    /// </remarks>
    private static readonly byte[] SlotExemplar =
    {
      19, // Neutral   MASONRY
      13, // Grass     GRASS
      14, // DryGrass  DRY_GRASS
      10, // Sand      SAND
      11, // Soil      DIRT
      7,  // Rock      STONE
      15, // Snow      SNOW
      12  // Wetland   MUD
    };

    /// <summary>
    /// The blocks <see cref="FieldPalette.Materials"/> lays out, in reading order.
    /// </summary>
    /// <remarks>
    /// Six of the sixteen are on <c>SurfaceSlot.Rock</c>, and that is the point of the selection rather than an
    /// oversight: granite, basalt, limestone, gravel and two ores share one texture and differ only in the
    /// vertex tint, so those are where <c>tint_strength</c> and <c>tint_is_srgb</c> are judged. Dragging
    /// <c>tint_strength</c> to zero should collapse all six into the same rock.
    /// </remarks>
    private static readonly byte[] MaterialField =
    {
      13, 14, 10, 11, // GRASS, DRY_GRASS, SAND, DIRT
      12, 15, 2, 4,   // MUD, SNOW, ICE, GRANITE
      5, 8, 9, 20,    // BASALT, LIMESTONE, GRAVEL, COBBLESTONE
      19, 32, 23, 16  // MASONRY, ORE_GOLD_RICH, ORE_COPPER_RICH, BLIGHTED_GRASS
    };

    private FieldPalette _palette = FieldPalette.Slots;
    private FieldRelief _relief = FieldRelief.Domes;
    private FieldColumn _column = FieldColumn.Uniform;
    private float _grassFieldBegin;
    private float _grassFieldEnd;
    private float _grassFieldFalloff = 1.0f;
    private int _patchVoxels = 16;
    private float _reliefMetres = 12.0f;
    private float _domeRadius = 0.85f;
    private bool _showLabels = true;
    private bool _showDebugView;

    /// <summary>Which materials the field is made of.</summary>
    [Export]
    public FieldPalette Palette
    {
      get => _palette;
      set { _palette = value; Rebuild(); }
    }

    /// <summary>Flat for reading boundaries, domes for reading slope.</summary>
    [Export]
    public FieldRelief Relief
    {
      get => _relief;
      set { _relief = value; Rebuild(); }
    }

    /// <summary>Uniform for judging art, layered for checking the mesher reads a real column the same way.</summary>
    [Export]
    public FieldColumn Column
    {
      get => _column;
      set { _column = value; Rebuild(); }
    }

    /// <summary>How wide one material's patch is, in voxels.</summary>
    [Export(PropertyHint.Range, "4,48,1")]
    public int PatchVoxels
    {
      get => _patchVoxels;
      set { _patchVoxels = Math.Max(4, value); Rebuild(); }
    }

    /// <summary>
    /// How tall each dome stands, in metres.
    /// </summary>
    /// <remarks>
    /// The default clears <c>cliff_end</c>. A dome's steepest point is <c>height * pi / (2 * radius)</c>, so
    /// twelve metres over a 6.8 m radius reaches about 70 degrees - past the 68 degrees at which the shader has
    /// moved all loose cover onto rock. Lower it and the cliff ramp is never fully crossed.
    /// </remarks>
    [Export(PropertyHint.Range, "0,40,0.5")]
    public float ReliefMetres
    {
      get => _reliefMetres;
      set { _reliefMetres = Math.Max(0.0f, value); Rebuild(); }
    }

    /// <summary>How much of a patch the dome covers. Below one it lands on flat ground inside its own patch.</summary>
    [Export(PropertyHint.Range, "0.2,1.0,0.01")]
    public float DomeRadius
    {
      get => _domeRadius;
      set { _domeRadius = Mathf.Clamp(value, 0.2f, 1.0f); Rebuild(); }
    }

    /// <summary>Whether each patch is named in the viewport.</summary>
    [Export]
    public bool ShowLabels
    {
      get => _showLabels;
      set { _showLabels = value; Rebuild(); }
    }

    /// <summary>Swaps in <see cref="DebugMaterial"/>, whose <c>debug_view</c> picks what to display.</summary>
    [Export]
    public bool ShowDebugView
    {
      get => _showDebugView;
      set { _showDebugView = value; Rebuild(); }
    }

    /// <summary>
    /// The material under test. Its own resource, never <c>terrain.tres</c> - see the class remarks.
    /// </summary>
    /// <summary>
    /// Where the far-field grass tint starts and finishes, in metres from the middle of the field.
    /// </summary>
    /// <remarks>
    /// <c>grass_field_correction</c> is the colour the ground is pushed towards once the grass field has thinned
    /// out, and it is ramped in by distance between two <b>global</b> shader parameters that only
    /// <c>TerrainGrass</c> publishes. The testbed has no grass field and so published neither, which left the
    /// globals at their project defaults - a zero-width band, which the shader reads as "no field" and skips. So
    /// the correction slider on the material was a transport to <c>terrain.tres</c> and nothing else: it could be
    /// carried across but never seen. Setting <see cref="GrassFieldEnd"/> above
    /// <see cref="GrassFieldBegin"/> turns the ramp on here, measured from the centre of the field outwards, so
    /// that near ground and far ground are both on screen at once and the colour can be judged against a fixed
    /// distance rather than against a memory of the last login.
    ///
    /// <para>
    /// Both default to zero, which is off. That keeps the field flat-lit for the art-judging the testbed is
    /// mostly used for, where a colour that changes with distance is exactly what is not wanted.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,200,1")]
    public float GrassFieldBegin
    {
      get => _grassFieldBegin;
      set { _grassFieldBegin = Math.Max(0.0f, value); PublishGrassField(); }
    }

    /// <inheritdoc cref="GrassFieldBegin"/>
    [Export(PropertyHint.Range, "0,200,1")]
    public float GrassFieldEnd
    {
      get => _grassFieldEnd;
      set { _grassFieldEnd = Math.Max(0.0f, value); PublishGrassField(); }
    }

    /// <summary>How hard the tint arrives across that band, matching <c>GrassLod.Sharpen</c>'s exponent.</summary>
    /// <remarks>
    /// In game this is not a setting - it is whatever the level-of-detail controller happens to be spending to
    /// stay inside its instance budget, so it moves while the player walks. One here, meaning the plain squared
    /// ramp, is the value to judge a colour at; turning it up shows what the tint looks like when the field is
    /// under pressure and arriving early.
    /// </remarks>
    [Export(PropertyHint.Range, "1,6,0.1")]
    public float GrassFieldFalloff
    {
      get => _grassFieldFalloff;
      set { _grassFieldFalloff = Math.Max(1.0f, value); PublishGrassField(); }
    }

    [Export] public ShaderMaterial TerrainMaterial { get; set; }

    /// <summary>The debug twin, shown instead when <see cref="ShowDebugView"/> is on.</summary>
    [Export] public ShaderMaterial DebugMaterial { get; set; }

    [ExportToolButton("Rebuild terrain")]
    public Callable RebuildButton => Callable.From(Rebuild);

    [ExportToolButton("Reload slot textures")]
    public Callable ReloadTexturesButton => Callable.From(ReloadTextures);

    [ExportToolButton("Copy tuning to terrain.tres")]
    public Callable CopyTuningButton => Callable.From(CopyTuningToShippingMaterial);

    /// <summary>
    /// The assembled slot textures, kept across rebuilds.
    /// </summary>
    /// <remarks>
    /// Building them is sixteen 512x512 images plus their mip pyramids, which is a visible pause on a node whose
    /// other settings are meant to be dragged. Nothing but the art on disk changes them, so the button above is
    /// the only thing that needs to drop them.
    /// </remarks>
    private TerrainSlotTextures.Assembled? _textures;

    public override void _Ready() => Rebuild();

    /// <summary>
    /// Puts the far-field grass ramp on the globals the terrain shader reads it from.
    /// </summary>
    /// <remarks>
    /// Globals rather than material parameters because two shaders read them - <c>terrain.gdshader</c> and its
    /// debug twin - which is the same reason <c>TerrainGrass.PublishField</c> sets them that way in game. Nothing
    /// here is per-frame: the focus is the middle of the field and does not move, so this runs when one of the
    /// three settings changes and on rebuild, rather than out of <c>_Process</c>.
    ///
    /// <para>
    /// The focus carries the field's own ground height. Distance in the shader is measured in three dimensions,
    /// so leaving it at zero would put the focus twelve metres underground and start the ramp early by however
    /// much of that the camera angle turned into horizontal distance.
    /// </para>
    /// </remarks>
    private void PublishGrassField()
    {
      // Begin above end is a band the shader would read backwards, so it is clamped to off rather than trusted.
      var end = Math.Max(_grassFieldBegin, _grassFieldEnd);

      RenderingServer.GlobalShaderParameterSet(
        "grass_field_focus", new Vector3(0.0f, (BaseElevation + 0.5f) * VoxelSize, 0.0f));
      RenderingServer.GlobalShaderParameterSet("grass_field_begin", _grassFieldBegin);
      RenderingServer.GlobalShaderParameterSet("grass_field_end", end);
      RenderingServer.GlobalShaderParameterSet("grass_field_falloff", _grassFieldFalloff);
    }

    /// <summary>Reassembles the texture arrays from whatever is in <c>Game/World/Shader/Slots</c> now.</summary>
    public void ReloadTextures()
    {
      _textures = null;
      Rebuild();
    }

    /// <summary>
    /// Throws the field away and builds it again.
    /// </summary>
    /// <remarks>
    /// Guarded on <see cref="Node.IsNodeReady"/> because every export setter calls it, and those fire while the
    /// scene is still being deserialised - once per property, on a node that is not in the tree yet.
    /// </remarks>
    public void Rebuild()
    {
      if (!IsNodeReady())
      {
        return;
      }

      PublishGrassField();

      var existing = GetNodeOrNull<Node3D>("Generated");
      if (existing != null)
      {
        RemoveChild(existing);
        existing.QueueFree();
      }

      var generated = new Node3D { Name = "Generated" };
      AddChild(generated);

      var appearance = BuildAppearance();
      var source = BuildChunks();
      var material = ApplyTextures(ShowDebugView ? DebugMaterial : TerrainMaterial);

      // Centred on the origin so the camera in the scene frames it whatever the patch size is, and so the
      // triplanar coordinates stay small - the world-space UVs this shader derives are measured from zero.
      generated.Position = new Vector3(
        -Columns * PatchVoxels * VoxelSize * 0.5f,
        0.0f,
        -Rows * PatchVoxels * VoxelSize * 0.5f);

      var chunksX = ChunksAcross(Columns);
      var chunksY = ChunksAcross(Rows);
      var triangles = 0;

      for (var chunkY = 0; chunkY < chunksY; chunkY++)
      {
        for (var chunkX = 0; chunkX < chunksX; chunkX++)
        {
          var mesh = SurfaceNets.Build(
            source, new ChunkKey(chunkX, chunkY, 0), appearance, VoxelSize, ChunkWrap.None);

          var surface = mesh?.Terrain;
          if (surface == null || surface.IsEmpty)
          {
            continue;
          }

          generated.AddChild(new MeshInstance3D
          {
            Name = $"Chunk {chunkX},{chunkY}",
            Mesh = ToArrayMesh(surface),
            MaterialOverride = material
          });

          triangles += surface.TriangleCount;
        }
      }

      if (ShowLabels)
      {
        AddLabels(generated);
      }

      GD.Print(
        $"[testbed] {Palette} / {Relief}: {triangles} tris over {chunksX}x{chunksY} chunks, material " +
        $"{material?.ResourcePath ?? "MISSING"} on {material?.Shader?.ResourcePath ?? "no shader"}");
    }

    /// <summary>Chunks needed to cover this many patches, rounded up.</summary>
    private int ChunksAcross(int patches) => (patches * PatchVoxels + ChunkSize - 1) / ChunkSize;

    /// <summary>
    /// Puts the assembled slot textures on the material, and nothing else.
    /// </summary>
    /// <remarks>
    /// The reference tints go on too, because they are a measurement of the art rather than a setting: the
    /// shader divides the vertex tint by them, so a material without them applies every tint against white and
    /// every block comes out muddy - which reads as the textures not having loaded.
    /// </remarks>
    private ShaderMaterial ApplyTextures(ShaderMaterial material)
    {
      if (material == null)
      {
        GD.PushError("[testbed] no material assigned - the field will draw with Godot's white fallback");
        return null;
      }

      var textures = _textures ??= TerrainSlotTextures.Build();

      material.SetShaderParameter("albedo_height", textures.Albedo);
      material.SetShaderParameter("normal_rough_ao", textures.Surface);
      material.SetShaderParameter("slot_reference_tint", textures.ReferenceTints);

      // The field is a few dozen metres across and sits on the origin, so there is no precision to protect and
      // every origin can be zero. Set rather than left alone because the shader's array is sixteen long, and a
      // material authored when it was eight leaves the rest at whatever Godot filled the tail with.
      material.SetShaderParameter("slot_uv_origin", new Vector3[BlockAppearance.Slots]);

      return material;
    }

    /// <summary>
    /// Copies the artist-facing parameters from the testbed material onto the shipping one and saves it.
    /// </summary>
    /// <remarks>
    /// Named one by one rather than looped over the shader's uniform list, and that is the whole safety
    /// property: the texture arrays and the reference tints are runtime-built resources with no path, and
    /// writing either into <c>terrain.tres</c> would persist an empty <c>Texture2DArray</c> into the file that
    /// ships.
    /// </remarks>
    public void CopyTuningToShippingMaterial()
    {
      const string ShippingPath = "res://Game/World/Shader/terrain.tres";

      if (TerrainMaterial == null)
      {
        GD.PushError("[testbed] nothing to copy - no material assigned");
        return;
      }

      var shipping = GD.Load<ShaderMaterial>(ShippingPath);
      if (shipping == null)
      {
        GD.PushError($"[testbed] {ShippingPath} did not load");
        return;
      }

      string[] tuning =
      {
        "normal_strength", "ao_strength",
        "slot_uv_scale", "slot_height_contrast",
        "height_blend_range",
        "tint_strength", "tint_is_srgb",
        "triplanar_sharpness",
        "cliff_start", "cliff_end",
        "wetness_darkening", "wetness_roughness",
        "snow_colour", "snow_melt_celsius",
        "grass_field_correction"
      };

      foreach (var parameter in tuning)
      {
        shipping.SetShaderParameter(parameter, TerrainMaterial.GetShaderParameter(parameter));
      }

      var error = ResourceSaver.Save(shipping, ShippingPath);

      GD.Print(error == Error.Ok
        ? $"[testbed] copied {tuning.Length} parameters to {ShippingPath}"
        : $"[testbed] could not save {ShippingPath}: {error}");
    }

    /// <summary>
    /// The palette the mesher is driven with: one entry per patch, ids running from one.
    /// </summary>
    /// <remarks>
    /// <c>BlockAppearance.From</c> is public for exactly this - it is what lets a caller mesh over invented
    /// materials rather than the shipped two dozen. <see cref="FieldPalette.Materials"/> could have used
    /// <c>BlockAppearance.Current</c> directly and does not, so that both layouts index their patch table the
    /// same way and only this method knows which is which.
    /// </remarks>
    private BlockAppearance BuildAppearance()
    {
      var blocks = new List<BlockAppearance.Block>(Columns * Rows);

      for (var patch = 0; patch < Columns * Rows; patch++)
      {
        var slot = Palette == FieldPalette.Slots
          ? (BlockAppearance.SurfaceSlot)patch
          : BlockAppearance.Current.SlotOf(MaterialField[patch]);

        blocks.Add(new BlockAppearance.Block
        {
          Id = BlockIdOf(patch),
          Name = NameOf(patch),
          Solid = true,
          Surface = BlockAppearance.SurfaceKind.Terrain,
          Slot = slot,
          Colour = ColourOf(patch)
        });
      }

      // Always registered, whatever Column is set to. A block id the appearance does not know reads back as the
      // Neutral slot and a transparent-black tint, so a field switched to Layered against a table built without
      // them would render as holes rather than as anything diagnosable.
      blocks.Add(new BlockAppearance.Block
      {
        Id = SubsoilId,
        Name = "subsoil",
        Solid = true,
        Surface = BlockAppearance.SurfaceKind.Terrain,
        Slot = BlockAppearance.SurfaceSlot.Soil,
        Colour = new Color(0.38f, 0.28f, 0.19f)
      });

      blocks.Add(new BlockAppearance.Block
      {
        Id = BedrockId,
        Name = "bedrock",
        Solid = true,
        Surface = BlockAppearance.SurfaceKind.Terrain,
        Slot = BlockAppearance.SurfaceSlot.Rock,
        Colour = new Color(0.55f, 0.53f, 0.49f)
      });

      return BlockAppearance.From(blocks);
    }

    /// <summary>The invented id a patch's material carries. One-based, because zero is air.</summary>
    private static byte BlockIdOf(int patch) => (byte)(patch + 1);

    /// <summary>
    /// What <see cref="FieldColumn.Layered"/> puts under the cap, past the sixteen ids the patches use.
    /// </summary>
    /// <remarks>
    /// Their tints are <c>DIRT</c>'s and <c>STONE</c>'s from the shipping palette rather than anything invented,
    /// because the point of the mode is to reproduce what the generator writes and being wrong about the colour
    /// of the thing that should not be visible would make a failure harder to recognise, not easier.
    /// </remarks>
    private const byte SubsoilId = 17;

    private const byte BedrockId = 18;

    /// <summary>How much subsoil <see cref="FieldColumn.Layered"/> lays under the cap, in whole voxels.</summary>
    /// <remarks>
    /// Two, which is mid-range for what <c>BiomeStage</c>'s residual soil depth gives on flat ground. The cap
    /// above it is one voxel and partial, exactly as <c>ChunkMaterializer</c> writes it - that ratio is the
    /// whole content of this mode and the reason it is not simply a thicker cover.
    /// </remarks>
    private const int SubsoilVoxels = 2;

    private string NameOf(int patch) => Palette == FieldPalette.Slots
      ? $"{patch} {SlotName(patch)}"
      : BlockAppearance.Current.NameOf(MaterialField[patch]);

    /// <summary>
    /// What a slot ordinal is called, or <c>unused</c> for one the enum has not named.
    /// </summary>
    /// <remarks>
    /// Spelt out rather than left to <c>ToString</c>, which renders an undefined enum value as its own number -
    /// so the label for slot twelve read "12 12" and looked like a bug in the layout rather than like a slot
    /// nobody has claimed.
    /// </remarks>
    private static string SlotName(int slot) =>
      Enum.IsDefined((BlockAppearance.SurfaceSlot)slot)
        ? ((BlockAppearance.SurfaceSlot)slot).ToString()
        : "unused";

    /// <summary>The tint a patch is drawn with, taken from the shipping palette wherever one exists.</summary>
    private Color ColourOf(int patch)
    {
      if (Palette == FieldPalette.Materials)
      {
        return BlockAppearance.Current.ColourOf(MaterialField[patch]);
      }

      return patch < SlotExemplar.Length
        ? BlockAppearance.Current.ColourOf(SlotExemplar[patch])
        : new Color(0.5f, 0.5f, 0.5f);
    }

    /// <summary>The whole field as chunks, with the band scan the mesher needs alongside.</summary>
    private Field BuildChunks()
    {
      var field = new Field();

      for (var chunkY = 0; chunkY < ChunksAcross(Rows); chunkY++)
      {
        for (var chunkX = 0; chunkX < ChunksAcross(Columns); chunkX++)
        {
          field.Put(BuildChunk(chunkX, chunkY));
        }
      }

      return field;
    }

    /// <summary>
    /// One chunk: every column solid to its surface elevation, in that column's own material.
    /// </summary>
    /// <remarks>
    /// Uniform all the way down by default rather than a cover over bedrock, which is what the generator writes.
    /// A cover would put a second material within reach of every vertex on a steep flank - realistic, and the
    /// opposite of what a testbed wants, since the flank is where the material under test is meant to be shown
    /// alone. <see cref="FieldColumn.Layered"/> writes the generator's column instead, for the times when the
    /// question is about the mesher rather than about the art.
    /// </remarks>
    private VoxelChunk BuildChunk(int chunkX, int chunkY)
    {
      var blocks = new byte[ChunkSize * ChunkSize * ChunkHeight];
      var occupancy = new byte[ChunkSize * ChunkSize * ChunkHeight];

      for (var localY = 0; localY < ChunkSize; localY++)
      {
        for (var localX = 0; localX < ChunkSize; localX++)
        {
          var voxelX = chunkX * ChunkSize + localX;
          var voxelY = chunkY * ChunkSize + localY;

          var block = BlockIdOf(PatchAt(voxelX, voxelY));
          var elevation = ElevationAt(voxelX, voxelY);

          var top = Math.Clamp((int)Math.Floor(elevation), 0, ChunkHeight - 1);
          var fraction = elevation - top;
          var offset = (localY * ChunkSize + localX) * ChunkHeight;

          var subsoilFrom = Column == FieldColumn.Layered ? Math.Max(0, top - SubsoilVoxels) : top;

          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = Column == FieldColumn.Uniform
              ? block
              : z >= subsoilFrom
                ? SubsoilId
                : BedrockId;

            occupancy[offset + z] = 255;
          }

          if (fraction > 0.0)
          {
            blocks[offset + top] = block;

            // At least one, because occupancy zero is the definition of air and would knock a hole in the
            // ground wherever the surface happened to land on a whole voxel.
            occupancy[offset + top] = (byte)Math.Clamp((int)Math.Round(fraction * 255.0), 1, 255);
          }
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, ChunkSize, ChunkHeight, blocks, occupancy);
    }

    /// <summary>Which patch a voxel column belongs to, clamped so the chunks past the grid continue its edge.</summary>
    private int PatchAt(int voxelX, int voxelY)
    {
      var column = Math.Clamp(voxelX / PatchVoxels, 0, Columns - 1);
      var row = Math.Clamp(voxelY / PatchVoxels, 0, Rows - 1);

      return row * Columns + column;
    }

    /// <summary>
    /// The surface height of a voxel column, in voxels above sea level.
    /// </summary>
    /// <remarks>
    /// The dome is a raised cosine, so it meets the plain with zero gradient - the patch boundary is genuinely
    /// flat, and every boundary in the field can be read without a slope of its own confusing what the blend is
    /// doing. Half a voxel is added throughout so the surface never lands exactly on a lattice plane, where the
    /// partially filled voxel that carries this shape would round away to nothing.
    /// </remarks>
    private double ElevationAt(int voxelX, int voxelY)
    {
      var plain = BaseElevation + 0.5;

      if (Relief == FieldRelief.Flat || ReliefMetres <= 0.0f)
      {
        return plain;
      }

      var patch = PatchAt(voxelX, voxelY);
      var centreX = (patch % Columns + 0.5) * PatchVoxels - 0.5;
      var centreY = (patch / Columns + 0.5) * PatchVoxels - 0.5;

      var radius = PatchVoxels * 0.5 * DomeRadius;
      var distance = Math.Sqrt(
        (voxelX - centreX) * (voxelX - centreX) + (voxelY - centreY) * (voxelY - centreY));

      if (distance >= radius)
      {
        return plain;
      }

      return plain + ReliefMetres / VoxelSize * 0.5 * (1.0 + Math.Cos(Math.PI * distance / radius));
    }

    /// <summary>Names every patch in the viewport, so a slot can be found without counting squares.</summary>
    private void AddLabels(Node3D generated)
    {
      for (var patch = 0; patch < Columns * Rows; patch++)
      {
        var centreX = (patch % Columns + 0.5) * PatchVoxels - 0.5;
        var centreY = (patch / Columns + 0.5) * PatchVoxels - 0.5;
        var height = ElevationAt((int)centreX, (int)centreY);

        generated.AddChild(new Label3D
        {
          Name = $"Label {patch}",
          Text = NameOf(patch),
          FontSize = 48,

          // Fixed size, so the back row is as legible as the front one. A world-space label shrinks with
          // distance, and on a field seen from one corner that puts a sixth of the slots past reading.
          FixedSize = true,
          PixelSize = 0.0007f,
          Billboard = BaseMaterial3D.BillboardModeEnum.Enabled,
          NoDepthTest = true,
          OutlineSize = 12,
          Position = new Vector3(
            (float)centreX * VoxelSize,
            (float)height * VoxelSize + 2.0f,
            (float)centreY * VoxelSize)
        });
      }
    }

    /// <summary>
    /// The array shapes <c>ArrayMesh</c> wants, mirroring <c>TerrainRenderer.Apply</c>.
    /// </summary>
    /// <remarks>
    /// The format flags are not optional. Godot takes the presence of a custom channel from the array being
    /// non-null but its layout only from these bits, and zero means the neighbour of <c>RGBA8_UNORM</c> in the
    /// enum rather than a default - so omitting them reads the sixteen weight bytes as something else. Getting
    /// the lengths wrong is worse: the surface is refused outright and the mesh draws nothing, which looks
    /// exactly like the mesher having returned nothing.
    /// </remarks>
    private static ArrayMesh ToArrayMesh(ChunkSurface surface)
    {
      var arrays = new Godot.Collections.Array();
      arrays.Resize((int)Godot.Mesh.ArrayType.Max);
      arrays[(int)Godot.Mesh.ArrayType.Vertex] = surface.Vertices;
      arrays[(int)Godot.Mesh.ArrayType.Normal] = surface.Normals;
      arrays[(int)Godot.Mesh.ArrayType.Color] = surface.Colours;
      arrays[(int)Godot.Mesh.ArrayType.Index] = surface.Indices;
      arrays[(int)Godot.Mesh.ArrayType.Custom0] = surface.SlotWeights0;
      arrays[(int)Godot.Mesh.ArrayType.Custom1] = surface.SlotWeights1;
      arrays[(int)Godot.Mesh.ArrayType.Custom2] = surface.SlotWeights2;
      arrays[(int)Godot.Mesh.ArrayType.Custom3] = surface.SlotWeights3;

      const uint Rgba8 = (uint)Godot.Mesh.ArrayCustomFormat.Rgba8Unorm;

      var format = (Godot.Mesh.ArrayFormat)(
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom0Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom1Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom2Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom3Shift));

      var mesh = new ArrayMesh();
      mesh.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays, null, null, format);

      return mesh;
    }

    /// <summary>A chunk source over a plain dictionary, which is all the mesher ever asks for.</summary>
    private sealed class Field : IChunkSource
    {
      private readonly Dictionary<ChunkKey, VoxelChunk> _chunks = new();
      private readonly Dictionary<ChunkKey, ChunkBands> _bands = new();

      public void Put(VoxelChunk chunk)
      {
        var key = new ChunkKey(chunk.ChunkX, chunk.ChunkY, chunk.ChunkZ);

        _chunks[key] = chunk;
        _bands[key] = ChunkBands.Of(chunk);
      }

      public VoxelChunk Get(ChunkKey key) => _chunks.TryGetValue(key, out var chunk) ? chunk : null;

      public ChunkBands BandsOf(ChunkKey key) => _bands.TryGetValue(key, out var bands) ? bands : null;
    }
  }
}
