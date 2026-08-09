using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The terrain shader, its debug twin, and the two things the CPU has to tell them.
  /// </summary>
  /// <remarks>
  /// Loaded by path rather than exported into the scene, because <c>game.gd</c> constructs
  /// <see cref="TerrainRenderer"/> in code - so its <c>[Export] Material</c> is always null and always was. The
  /// same reason <c>walkable_floor.gd</c> is loaded by path a few lines further down in that file.
  ///
  /// <para>
  /// <b>Nothing here throws or hard-fails.</b> A shader that will not compile leaves
  /// <see cref="Shipping"/> null and the renderer falls back to the <c>StandardMaterial3D</c> it has always had.
  /// That matters more than it sounds: an unassigned material does not render plain terrain, it renders nothing
  /// at all, and "the world is missing" is a much worse first symptom of a typo in a shader than "the world went
  /// back to looking flat".
  /// </para>
  /// </remarks>
  public sealed class TerrainMaterials
  {
    private const string Directory = "res://Game/World/Shader/";

    /// <summary>What terrain is drawn with.</summary>
    public ShaderMaterial Shipping { get; private init; }

    /// <summary>The same shader with its intermediate values exposed. Null if it failed to load.</summary>
    public ShaderMaterial Debug { get; private init; }

    /// <summary>Metres per texture tile, per slot, read back from the material so the CPU can snap to it.</summary>
    private float[] _uvScale;

    /// <summary>Where the snapped origins were last computed for, so a metre of walking is not eight divisions.</summary>
    private Vector3 _originAnchor;
    private bool _hasOriginAnchor;

    /// <summary>
    /// Loads both materials, or returns null if the shipping one is unusable.
    /// </summary>
    /// <remarks>
    /// The debug material is allowed to be missing on its own - it is a development aid, and losing it should
    /// cost the key binding rather than the terrain.
    /// </remarks>
    public static TerrainMaterials Load()
    {
      var shipping = GD.Load<ShaderMaterial>($"{Directory}terrain.tres");

      if (shipping?.Shader == null)
      {
        GD.PushError(
          "[terrain] terrain.tres did not load, or its shader failed to compile. Falling back to the flat " +
          "vertex-colour material - terrain will render, without textures.");

        return null;
      }

      var materials = new TerrainMaterials
      {
        Shipping = shipping,
        Debug = GD.Load<ShaderMaterial>($"{Directory}terrain_debug.tres")
      };

      materials.EnsurePlaceholderArray();
      materials._uvScale = shipping.GetShaderParameter(SlotUvScale).AsFloat32Array();

      return materials;
    }

    private static readonly StringName AlbedoHeight = "albedo_height";
    private static readonly StringName SlotUvScale = "slot_uv_scale";
    private static readonly StringName SlotUvOrigin = "slot_uv_origin";

    /// <summary>
    /// Gives every slot a white texture when none has been authored.
    /// </summary>
    /// <remarks>
    /// Built here rather than shipped as an asset, because the asset would be eight white pixels per layer and a
    /// binary blob in the repository to hold them. White is also not an arbitrary placeholder: the shader
    /// corrects a sampled albedo towards the vertex tint by dividing out what the texture already is, so a white
    /// texture against a white reference tint reproduces the vertex-colour renderer this replaces, pixel for
    /// pixel. That makes the first run of the shader a comparison rather than a leap.
    ///
    /// <para>
    /// Skipped entirely once a real array is assigned, so this never overwrites an artist's work.
    /// </para>
    /// </remarks>
    private void EnsurePlaceholderArray()
    {
      if (Shipping.GetShaderParameter(AlbedoHeight).As<Texture2DArray>() != null)
      {
        return;
      }

      var images = new Godot.Collections.Array<Image>();

      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        var image = Image.CreateEmpty(8, 8, false, Image.Format.Rgba8);

        // Opaque white: albedo one, and height one for the phase that starts reading the alpha.
        image.Fill(Colors.White);
        images.Add(image);
      }

      var array = new Texture2DArray();
      array.CreateFromImages(images);

      Shipping.SetShaderParameter(AlbedoHeight, array);
      Debug?.SetShaderParameter(AlbedoHeight, array);
    }

    /// <summary>
    /// Moves the origin the triplanar coordinates are measured from to near the player.
    /// </summary>
    /// <remarks>
    /// Purely a floating-point measure, with no visual intent whatsoever - see <c>slot_uv_origin</c> in
    /// <c>terrain_common.gdshaderinc</c>. Vertices carry absolute world coordinates in a world 128 km across, and
    /// the derivative of a UV that large is too coarse to pick a mip level with, which shimmers.
    ///
    /// <para>
    /// Each slot is snapped to a whole number of <i>its own</i> tiles, so moving the origin shifts the texture by
    /// an exact number of repeats and changes nothing on screen. Snapping them all to one shared grid would
    /// instead demand that every slot's tile size divide it, and the first artist to type 3.0 into one would get
    /// a world that jumps as they walk.
    /// </para>
    /// </remarks>
    public void SetUvAnchor(Vector3 anchorMetres)
    {
      if (_uvScale == null || _uvScale.Length < BlockAppearance.Slots)
      {
        return;
      }

      // A chunk of movement changes nothing until it is far enough to matter, and this runs off the collision
      // anchor, which already only moves in chunk steps.
      if (_hasOriginAnchor && _originAnchor.DistanceSquaredTo(anchorMetres) < 64.0f * 64.0f)
      {
        return;
      }

      _originAnchor = anchorMetres;
      _hasOriginAnchor = true;

      var origins = new Vector3[BlockAppearance.Slots];

      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        var tile = Mathf.Max(_uvScale[slot], 0.0001f);

        origins[slot] = new Vector3(
          Mathf.Round(anchorMetres.X / tile) * tile,
          Mathf.Round(anchorMetres.Y / tile) * tile,
          Mathf.Round(anchorMetres.Z / tile) * tile);
      }

      Shipping.SetShaderParameter(SlotUvOrigin, origins);
      Debug?.SetShaderParameter(SlotUvOrigin, origins);
    }
  }
}
