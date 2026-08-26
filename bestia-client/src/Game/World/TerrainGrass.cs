using System.Collections.Generic;
using System.Diagnostics;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Scatters decorative grass over the ground that is drawn as grass.
  /// </summary>
  /// <remarks>
  /// <b>Not the same thing as the ground cover, and the difference is who owns it.</b> A herb, a shrub and a
  /// reed are server entities with ids, health and a <c>collect</c> block - see <c>GroundCoverScatter</c> and
  /// <see cref="StaticEntityRenderer"/> - and they are scattered on a three-metre lattice because every one of
  /// them costs a <c>world.createEntity</c> per column a player holds. That budget is what
  /// <c>GroundCoverParams.litterGain</c> spends, and it is why a meadow of them reads as a dozen plants in a
  /// field rather than as a field.
  ///
  /// <para>
  /// This is the field. It is drawn and nothing else: no ids, no server, no clicks, no collision, and no
  /// message on the wire. So it can run at whatever density looks right, and the only thing it costs is
  /// triangles.
  /// </para>
  ///
  /// <para>
  /// <b>Scattered on the terrain mesh rather than on the voxels under it.</b> The mesher already decided where
  /// the ground is and what it is made of: <see cref="ChunkSurface.Vertices"/> is the drawn surface, and
  /// <see cref="ChunkSurface.SlotWeights0"/> carries how much of each vertex is
  /// <see cref="BlockAppearance.SurfaceSlot.Grass"/>. Sampling that is exact by construction - a blade cannot
  /// end up hovering over ground the mesher put somewhere else, and grass fades out exactly where the texture
  /// does, because it is reading the same number the terrain shader is.
  /// </para>
  ///
  /// <para>
  /// <b>Two meshes, and most of the field is the cheaper one.</b> <c>grass</c>'s twelve-blade clump is a
  /// near-field layer; <c>grass2</c>'s single tuft, drawn tall enough to span as much ground, carries the field
  /// all the way to the fade. That is worth doing because fewer triangles per instance is not the same as fewer
  /// per square metre hidden - see <see cref="TuftPath"/> for the break-even, and <see cref="TuftDensity"/> for
  /// the measurement: the mix covers more ground within 20 m than one layer of clumps did, for 1.46 M triangles
  /// against 2.34 M. Two silhouettes is also what stops the field reading as one model repeated.
  /// </para>
  ///
  /// <para>
  /// <b>The unit of drawing is a cell, not a chunk.</b> Each chunk's grass is split across a world-aligned
  /// <see cref="CellMetres"/> grid, and each cell is one <see cref="MultiMesh"/> per layer whose density is set
  /// from that cell's own distance to the player. Doing it per chunk instead is the obvious thing and it does not work:
  /// a chunk is 32 m across, so one whose near corner is under the player's feet also reaches past the fade
  /// radius, and measuring it by its nearest point draws the whole thousand square metres at full density.
  /// Measured on nine by nine grassy chunks, that was 2.1 M triangles where cells make it under a million.
  /// </para>
  ///
  /// <para>
  /// <b>To the player, and never to the camera.</b> How much grass stands on a patch of ground is a fact about
  /// that ground and about where the player is; it cannot be a fact about how far back the camera happens to be
  /// sitting. Measuring from the eye made zooming out empty the field - see <see cref="SetFocusAt"/> for the
  /// measurement. How much ground is *on screen* does depend on the zoom, and that is answered separately, by
  /// <see cref="ZoomResponse"/> widening the band.
  /// </para>
  ///
  /// <para>
  /// <b>Density falls off through <see cref="MultiMesh.VisibleInstanceCount"/>.</b> That property truncates the
  /// instance buffer, so a cell's grass is a prefix of its own transforms. The scatter shuffles each cell
  /// before uploading it - which is the entire reason it does - so a prefix is a uniform random subset of the
  /// cell rather than whichever triangles the mesher happened to emit first. Changing the count is one integer
  /// per cell per frame, with no rebuild and no reupload.
  /// </para>
  ///
  /// <para>
  /// <b>Thinning a cell does not have to bare its ground.</b> Coverage is <c>count * footprint</c>, so a cell
  /// cut to a fraction of its clumps holds its coverage if what is left grows by the inverse square root of
  /// that fraction - see <see cref="GrassLod.CoverageScale"/>. What falls off with distance is then the grain
  /// of the field rather than how much of the ground it hides, which is what lets <see cref="FadeOutMetres"/>
  /// reach much further for the same triangles, and what makes <see cref="MaxVisibleInstances"/> able to pin
  /// the cost flat across the whole zoom range.
  /// </para>
  ///
  /// <para>
  /// Shadows are off. A shadow pass over tens of thousands of instances costs about what the colour pass does,
  /// and grass is the one thing in the world whose shadow nobody can pick out from the grass next to it.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class TerrainGrass : Node3D
  {
    /// <summary>Twelve-blade clumps per square metre of fully grassy, flat ground.</summary>
    /// <remarks>
    /// The near-field layer. A clump is 130 triangles against the tuft's 72 for the same footprint, so this is
    /// the expensive half of the field and it is deliberately the one with the short band - see
    /// <see cref="TuftDensity"/> for what carries the rest.
    ///
    /// <para>
    /// Ground that is only partly grass gets proportionally less, because the weight it is multiplied by is the
    /// slot weight the terrain shader blends with - so a dune with green patches on it grows grass on the
    /// patches and nowhere else, without this having to know what a dune is.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,20,0.1")] public float Density { get; set; } = 2.0f;

    /// <summary>Height of a clump in metres, before <see cref="HeightSpread"/>.</summary>
    /// <remarks>
    /// The clump is scaled uniformly, so this is the model's whole scale and not only its height: at 1.2 m one
    /// spans about 1.53 m of ground where at 0.4 m it spanned 0.51 m. Nine times the ground per clump for the
    /// same 130 triangles, which is why raising this is by far the cheapest way to make the field read as
    /// full - see <see cref="Density"/>, which can come down to pay for it.
    ///
    /// <para>
    /// It is <b>taller than a herb</b>, which stands at 0.45 m, so the collectible ground cover is now inside
    /// the field rather than above it. That is deliberate and known: the plants worth picking are to be told
    /// apart by having their own model, not by being the tallest thing around.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.05,4,0.01")] public float Height { get; set; } = 1.2f;

    /// <summary>Half-width of the size spread around <see cref="Height"/>, as a share of it.</summary>
    [Export(PropertyHint.Range, "0,1,0.01")] public float HeightSpread { get; set; } = 0.35f;

    /// <summary>How far from the <b>player</b>, in metres, grass is drawn at its full <see cref="Density"/>.</summary>
    /// <remarks>
    /// From the player rather than from the camera, which is the whole of <see cref="SetFocusAt"/>'s reason -
    /// see it for why measuring from the eye made zooming out empty the field.
    ///
    /// <para>
    /// Both this and <see cref="FadeOutMetres"/> are the band at the reference zoom; the band actually used
    /// widens with the camera distance through <see cref="GrassLod.BandScale"/>.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,200,1")] public float FullDensityMetres { get; set; } = 15.0f;

    /// <summary>How far from the player, in metres, the last clump goes out.</summary>
    /// <remarks>
    /// Measured to the <i>nearest</i> point of a cell rather than to its middle, so a cell the player is
    /// standing at the edge of is at full density rather than at whatever a half-cell offset works out to.
    ///
    /// <para>
    /// Safe to turn up by eye, which it was not before: <see cref="MaxVisibleInstances"/> bounds what the
    /// increase can cost, so raising this makes the field reach further rather than making frames longer.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,400,1")] public float FadeOutMetres { get; set; } = 30.0f;

    /// <summary>Single tufts per square metre of fully grassy, flat ground.</summary>
    /// <remarks>
    /// <b>The body of the field.</b> Set near what <see cref="Density"/> alone used to be, because a tuft at
    /// <see cref="TuftHeight"/> spans 1.53 m against a 1.2 m clump's 1.53 m - the two cover the same ground, so
    /// an instance is an instance and this can carry the field on its own past
    /// <see cref="FadeOutMetres"/>.
    ///
    /// <para>
    /// Measured against the single-layer field it replaces: 4.5 tufts plus 2.0 clumps covers more ground within
    /// 20 m than 5.0 clumps did and costs <b>1.46 M triangles against 2.34 M</b>, because most of the field is
    /// now made of the cheaper mesh. That saving is what pays for the band reaching 60 m.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,20,0.1")] public float TuftDensity { get; set; } = 4.5f;

    /// <summary>Height of a tuft in metres, before <see cref="HeightSpread"/>.</summary>
    /// <remarks>
    /// <b>Above 1.34 m or this layer is a loss.</b> See <see cref="TuftPath"/>: the tuft is narrower for its
    /// height than the clump, so it only beats the clump on triangles per square metre covered once it is drawn
    /// tall enough to span as much. 1.8 m matches a 1.2 m clump's footprint and comes out 45% cheaper.
    ///
    /// <para>
    /// It stands 0.6 m taller than a clump, which is why the two layers cross-fade in density rather than
    /// swapping at a radius - no plant ever changes height, and both are present through the transition.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.05,4,0.01")] public float TuftHeight { get; set; } = 1.8f;

    /// <summary>How far from the player, in metres, tufts are drawn at their full <see cref="TuftDensity"/>.</summary>
    [Export(PropertyHint.Range, "0,200,1")] public float TuftFullDensityMetres { get; set; } = 15.0f;

    /// <summary>How far from the player, in metres, the last tuft goes out - and so where the field ends.</summary>
    [Export(PropertyHint.Range, "0,400,1")] public float TuftFadeOutMetres { get; set; } = 60.0f;

    /// <summary>Camera distance, in metres, that <see cref="FullDensityMetres"/> was chosen against.</summary>
    /// <remarks>
    /// Ten, the spring arm's own default <c>spring_length</c>, so the band at the default zoom is exactly the
    /// band named above and the numbers in the inspector mean what they say.
    /// </remarks>
    [Export(PropertyHint.Range, "1,60,1")] public float ReferenceZoomMetres { get; set; } = 10.0f;

    /// <summary>How hard the fade band follows the camera distance. 0 pins it, 1 tracks it linearly.</summary>
    /// <remarks>
    /// The knob that turns this feature off: at 0 the band is fixed at the numbers above whatever the zoom is,
    /// which is what the field did before. Half by default - see <see cref="GrassLod.BandScale"/> for why
    /// tracking the zoom linearly costs more than it is worth.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.05")] public float ZoomResponse { get; set; } = 0.5f;

    /// <summary>Ceiling on how far <see cref="ZoomResponse"/> may widen the band.</summary>
    [Export(PropertyHint.Range, "1,6,0.1")] public float MaxZoomScale { get; set; } = 2.5f;

    /// <summary>
    /// How much of a thinned cell's lost coverage to give back by drawing what is left of it larger.
    /// </summary>
    /// <remarks>
    /// One is full compensation, zero is the uncompensated field. Exported so the two can be compared by eye in
    /// the running game, because which of them looks better at distance is a judgement and not a calculation.
    /// See <see cref="GrassLod.CoverageScale"/> for the identity it rests on.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.05")] public float CoverageCompensation { get; set; } = 1.0f;

    /// <summary>Ceiling on that compensation, past which the field goes back to fading out.</summary>
    /// <remarks>
    /// It also sizes <see cref="MultiMeshInstance3D.ExtraCullMargin"/>, since a clump drawn at two and a half
    /// times its size stands well outside the bounds Godot computed from the transforms.
    /// </remarks>
    [Export(PropertyHint.Range, "1,4,0.1")] public float MaxCoverageScale { get; set; } = 2.5f;

    /// <summary>
    /// Clumps this may draw at once across the whole field, or 0 for no budget.
    /// </summary>
    /// <remarks>
    /// The draw-cost twin of <see cref="BuildBudgetMillis"/>, and what makes <see cref="FadeOutMetres"/> and
    /// <see cref="ZoomResponse"/> safe to turn up at all. Over budget, every cell is thinned by the same share
    /// rather than the far ones being cut - see <see cref="GrassLod.BudgetTrim"/>.
    ///
    /// <para>
    /// <b>This is not a backstop like <see cref="MaxPerChunk"/>; it is the knob that decides what the field
    /// costs.</b> The ground on screen grows with the square of the camera distance, so the band following the
    /// zoom asks for 84 thousand clumps at full zoom against 23 thousand at the default - eleven million
    /// triangles, from a field that measured 11.5 thousand clumps and 1.5 M before any of this.
    /// </para>
    ///
    /// <para>
    /// <b>What it buys is a flat cost across the whole zoom range.</b> The count is the same whether the camera
    /// is at 8 m or at 36 m, because whatever the trim takes in count the coverage compensation gives back in
    /// plant size: at full zoom that is 21% of them at 2.16 times the size, and <c>0.21 * 2.16²</c> is 1.
    /// Coverage is held exactly, and what changes with the zoom is the grain rather than how much ground is
    /// covered - which is the right way round, since at full zoom the plants are small on screen and their
    /// grain is the last thing anyone can pick out.
    /// </para>
    ///
    /// <para>
    /// <b>It counts instances, not triangles, and the two stopped being the same thing.</b> A tuft is 72
    /// triangles and a clump 130, so this is a <i>conservative</i> triangle bound: 18 thousand is at worst
    /// 2.34 M if every one of them were a clump, and at the default mix it measures 1.46 M. Erring that way is
    /// the right direction - the budget cannot be undersold by a field that leans cheap - but a reader sizing a
    /// frame from this number should multiply by the mix rather than by 130.
    /// </para>
    ///
    /// <para>
    /// It follows that this and <see cref="MaxCoverageScale"/> are tied. The compensation saturates below a
    /// fraction of <c>1 / MaxCoverageScale²</c>, which at 2.5 is 0.16, so a budget low enough to trim past that
    /// stops holding coverage and the field bares its ground again. Eighteen thousand trims to 0.21 and has
    /// headroom; twelve thousand would not.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,200000,1000")] public int MaxVisibleInstances { get; set; } = 18_000;

    /// <summary>
    /// Edge of one LOD cell, in metres. The granularity the density can vary at, and one draw call each.
    /// </summary>
    /// <remarks>
    /// The trade is entirely between draw calls and wasted triangles, and it is not close at either end. Coarse
    /// cells draw a whole cell at the density of its nearest corner; fine cells draw a few hundred multimeshes.
    /// Eight metres puts about a hundred cells inside the fade radius and keeps the step between one cell's
    /// density and its neighbour's small enough not to read as a seam.
    ///
    /// <para>
    /// Aligned to the world rather than to each chunk, so a cell straddling a chunk boundary is drawn as two
    /// halves at the same density instead of as two cells offset from one another.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "2,64,1")] public float CellMetres { get; set; } = 8.0f;

    /// <summary>
    /// Ceiling on the instances built for one chunk, whatever <see cref="Density"/> asks for.
    /// </summary>
    /// <remarks>
    /// A memory bound rather than a rendering one - the buffers are built for every chunk the terrain holds,
    /// including the ones beyond <see cref="FadeOutMetres"/> that will draw none of them, because the
    /// <see cref="ChunkSurface"/> they are scattered from is handed over once at install and is not kept.
    ///
    /// <para>
    /// A backstop rather than a knob. It is applied by scaling <see cref="Density"/> down for the chunk that
    /// would exceed it, so a capped chunk is uniformly thinner rather than bare on one side - but a chunk that
    /// hits it is drawn at a density its neighbours are not, which is a seam. The default is above what a
    /// wholly grassy 32 m chunk asks for at the default density, so nothing reaches it in ordinary play.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,40000,100")] public int MaxPerChunk { get; set; } = 8000;

    /// <summary>
    /// How upright ground has to be to grow grass, as the vertical component of its normal.
    /// </summary>
    /// <remarks>
    /// 0.6 is about 53 degrees. Grass is drawn standing straight up whatever it grows on, which is right for a
    /// slope and absurd for a wall: on a cliff face the blades would stand out of it sideways, and the
    /// surface-nets mesh has plenty of near-vertical triangles where a terrace steps down.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.01")] public float MinUpright { get; set; } = 0.6f;

    /// <summary>
    /// How long, in milliseconds, one frame may spend scattering newly arrived chunks.
    /// </summary>
    /// <remarks>
    /// Scattering a wholly grassy 32 m chunk at the default density measures at about 3 ms, and
    /// <c>TerrainRenderer.InstallsPerFrame</c> installs two chunks a frame - so without a budget, walking into
    /// a meadow would spend 6 ms a frame on grass alone, on top of the mesh upload that prompted it. This is
    /// what makes <see cref="Density"/> safe to turn up: raising it makes the field arrive over more frames
    /// rather than making those frames longer.
    ///
    /// <para>
    /// Checked between chunks and not within one, so a single chunk can overrun it. A chunk is the smallest
    /// thing that can be scattered at all - the alternative is a half-grassed chunk on screen - and the cap
    /// on <see cref="MaxPerChunk"/> is what bounds how far one can overrun by.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.1,16,0.1")] public float BuildBudgetMillis { get; set; } = 1.5f;

    /// <summary>The mesh one clump is drawn with, and the material to draw it with.</summary>
    /// <remarks>
    /// The larger of the two grass meshes, which is the cheaper one per square metre covered: a dozen blades
    /// spanning 2.85 m for 130 triangles, against <c>grass2</c>'s single tuft spanning 0.6 m for 72. Both are
    /// what <see cref="PropAppearance"/> gives the ground cover, so the field and the plants standing in it are
    /// the same art at different sizes.
    /// </remarks>
    private const string ClumpPath = "res://Game/Terrain/Grass/grass.res";

    private const string MaterialPath = "res://Game/Terrain/Grass/grass.tres";

    /// <summary>The other grass mesh: a single tuft, and what most of the field is actually made of.</summary>
    /// <remarks>
    /// <b>It is drawn taller than the clump, and that is the whole reason it is worth using.</b> Fewer
    /// triangles per instance is not the same as fewer per square metre of ground hidden, and the tuft is half
    /// again narrower for its height than the clump is - so at its authored proportions it costs about six
    /// times as much per unit of coverage. The break-even is 1.34 m; at <see cref="TuftHeight"/>'s 1.8 m it
    /// spans what a 1.2 m clump spans, for 72 triangles against 130.
    ///
    /// <para>
    /// Nobody can tell a stretched tuft from a rosette at the distances this carries, and up close the two
    /// silhouettes side by side are what stops the field reading as one model repeated.
    /// </para>
    /// </remarks>
    private const string TuftPath = "res://Game/Terrain/Grass/grass2.res";

    /// <summary>Height, in metres, that <see cref="TuftPath"/>'s mesh stands at above its own origin.</summary>
    /// <remarks><see cref="PropAppearance"/> carried the same number while the herbs were drawn from it.</remarks>
    private const float TuftNaturalHeight = 0.7053f;

    /// <summary>
    /// Which of the two meshes a patch draws. One <see cref="MultiMesh"/> holds one mesh, so a mixed field is
    /// two multimeshes per cell rather than one.
    /// </summary>
    /// <remarks>
    /// They are separate layers rather than a per-instance choice for a second reason beyond the multimesh:
    /// each wants its own band. The clumps are a near-field detail that stops early, the tufts carry the field
    /// all the way out - so what the player walks through is a mix and what they see at the fade is tufts, with
    /// no boundary where one mesh becomes the other. Cross-fading the two *densities* rather than swapping the
    /// mesh at a radius is what keeps the 0.6 m height difference between them from reading as a step.
    /// </remarks>
    private enum Layer
    {
      Clump = 0,
      Tuft = 1,
    }

    /// <summary>How many <see cref="Layer"/>s there are, for the arrays keyed on one.</summary>
    private const int Layers = 2;

    /// <summary>
    /// <c>grass2</c> was unwrapped with V at the base where <c>grass</c> has it at the tip.
    /// </summary>
    /// <remarks>
    /// A fact about the mesh and a <i>material</i> uniform, not a per-instance one, which is the only reason
    /// the tuft needs a material of its own at all. Getting it backwards is visible twice over: the blade is
    /// dark at the tip and bright at the root, and it bends from the wrong end.
    /// </remarks>
    private static readonly StringName UvVAtTip = "uv_v_at_tip";

    /// <summary>Height, in metres, that <see cref="ClumpPath"/>'s mesh stands at above its own origin.</summary>
    /// <remarks><see cref="PropAppearance"/> carries the same number for the same mesh, and for the same reason.</remarks>
    private const float ClumpNaturalHeight = 2.2391f;

    /// <summary>How much the coverage scale has to move before it is worth pushing to the shader.</summary>
    /// <remarks>A hundredth of a clump's size, which at a metre-tall clump is a centimetre and invisible.</remarks>
    private const float ScaleEpsilon = 0.01f;

    /// <summary>Cull margin, in metres, covering the wind - which moves vertices Godot's bounds know nothing about.</summary>
    /// <remarks>The same margin <see cref="StaticEntityRenderer"/>'s batches carry, for the same reason.</remarks>
    private const float WindCullMargin = 1.2f;

    /// <summary>One cell's worth of grass: a multimesh, and the box its clumps stand in.</summary>
    private sealed class Patch
    {
      internal MultiMeshInstance3D Node;
      internal MultiMesh Multi;

      /// <summary>Which mesh this cell draws, and so which band measures it.</summary>
      internal Layer Layer;

      /// <summary>The box the LOD measures its distance to.</summary>
      internal Vector3 Min;
      internal Vector3 Max;

      internal int Total;

      /// <summary>What <see cref="MultiMesh.VisibleInstanceCount"/> was last set to. -1 until the first pass.</summary>
      internal int Visible = -1;

      /// <summary>What was last pushed to the shader's per-instance scale. -1 until the first pass.</summary>
      internal float Scale = -1.0f;

      /// <summary>
      /// This frame's share of the cell, carried between the two passes of <see cref="_Process"/>.
      /// </summary>
      /// <remarks>
      /// Held here rather than in a list beside the patches so the second pass does not measure every cell's
      /// distance again, and so nothing has to be allocated per frame to pair the two up.
      /// </remarks>
      internal float Fraction;
    }

    /// <summary>Chunk -> its cells, so a re-mesh or a withdrawal takes all of them together.</summary>
    private readonly Dictionary<ChunkKey, List<Patch>> _patches = new();

    /// <summary>
    /// Chunks whose ground has arrived but whose grass has not been scattered yet.
    /// </summary>
    /// <remarks>
    /// Holds the <see cref="ChunkSurface"/> itself, which is the one real cost of deferring: those arrays are
    /// otherwise dropped the moment <see cref="TerrainRenderer"/> has uploaded them, and a login queues the
    /// whole view volume at once. It is transient - the queue drains at
    /// <see cref="BuildBudgetMillis"/> a frame and is empty within a second or two of the terrain settling.
    ///
    /// <para>
    /// A dictionary rather than a queue, so that a chunk re-meshed while still waiting replaces its own entry
    /// instead of being scattered twice from two versions of the same ground.
    /// </para>
    /// </remarks>
    private readonly Dictionary<ChunkKey, ChunkSurface> _pending = new();

    private Godot.Mesh _clump;
    private Godot.Mesh _tuft;
    private Material _material;
    private Material _tuftMaterial;
    private bool _loaded;

    /// <summary>Where the camera is looking, which is where the player is. Null until told - see <see cref="SetFocusAt"/>.</summary>
    private Vector3? _focus;

    /// <summary>
    /// The shader's per-instance scale boost. A <see cref="StringName"/> so the per-cell push allocates nothing.
    /// </summary>
    private static readonly StringName ExtraScaleParameter = "grass_extra_scale";

    /// <summary>
    /// Tells the field where the player is, so the level of detail can be measured from them.
    /// </summary>
    /// <remarks>
    /// <b>Measuring from the camera is what made zooming out empty the field.</b> The spring arm runs 8 m to
    /// 36 m from the player and the fade band spanned 12 m to 40 m, so the two were the same size: at full zoom
    /// the eye was 36 m from the ground under the player's feet, which put it in the last few metres of a fade
    /// that ended at 40 - about a fiftieth of the grass, for ground the player was standing on.
    ///
    /// <para>
    /// Density at a point on the ground is a property of that ground and of where the player is, never of how
    /// far back the camera happens to be sitting. How much ground is *on screen* does depend on the zoom, and
    /// that is answered separately by <see cref="ZoomResponse"/> widening the band.
    /// </para>
    ///
    /// <para>
    /// Called from <c>game.gd</c> beside <c>TerrainRenderer.SetCollisionAnchorAt</c>, off the same
    /// <c>get_owned_entity()</c> the collision anchor already resolves. The zoom is then derived here as the
    /// distance from this point to the camera rather than being plumbed through, so nothing needs to know that
    /// the camera is on a spring arm at all.
    /// </para>
    /// </remarks>
    public void SetFocusAt(Vector3 position) => _focus = position;

    /// <summary>
    /// Replaces this chunk's grass from the terrain surface that was just drawn for it.
    /// </summary>
    /// <remarks>
    /// Called by <see cref="TerrainRenderer"/> on every install, which means a re-mesh takes its grass with it.
    /// That is the opposite of how the props are wired - <c>game.gd</c> keeps those out of the terrain's
    /// lifecycle deliberately, because nothing standing on the ground should be torn down when the ground is
    /// patched - and it is right here for the mirror-image reason: this grass is not standing on the surface,
    /// it is a sample of it, so a surface that changed means a sample that is wrong.
    ///
    /// <para>
    /// The scatter is seeded from the chunk key alone, so a re-mesh puts every clump back where it was. Only
    /// the ground that actually changed moves.
    /// </para>
    ///
    /// <para>
    /// The old grass goes at once and the new grass is queued - see <see cref="BuildBudgetMillis"/> - so a
    /// re-meshed chunk is briefly bare. That is the right way round: the alternative is grass left standing on
    /// ground that has moved out from under it, and what usually prompts a re-mesh is a few voxels being dug.
    /// </para>
    /// </remarks>
    public void Build(ChunkKey key, ChunkSurface terrain)
    {
      Remove(key);

      if (terrain == null || terrain.IsEmpty || Density <= 0.0f || MaxPerChunk <= 0)
      {
        return;
      }

      var vertices = terrain.Vertices;
      var normals = terrain.Normals;
      var indices = terrain.Indices;
      var weights = terrain.SlotWeights0;

      // No weights, no idea which of this ground is grass. A surface built before the slot channels existed, or
      // by a test that did not fill them, gets no grass rather than grass everywhere.
      if (weights == null || vertices == null || normals == null ||
          weights.Length != vertices.Length * 4 || normals.Length != vertices.Length)
      {
        return;
      }

      if (!Load())
      {
        return;
      }

      // Queued rather than scattered here. See BuildBudgetMillis: this is called from the terrain's install
      // path, twice a frame, and a wholly grassy chunk is milliseconds of work.
      _pending[key] = terrain;
    }

    /// <summary>Scatters one queued chunk and puts its cells on screen.</summary>
    private void Raise(ChunkKey key, ChunkSurface terrain)
    {
      var cells = Scatter(key, terrain.Vertices, terrain.Normals, terrain.Indices, terrain.SlotWeights0);

      var total = 0;
      for (var layer = 0; layer < Layers; layer++)
      {
        total += cells[layer].Count;
      }

      if (total == 0)
      {
        return;
      }

      var patches = new List<Patch>(total);

      for (var layer = 0; layer < Layers; layer++)
      {
        foreach (var (cell, transforms) in cells[layer])
        {
          patches.Add(Install(key, cell, transforms, (Layer)layer));
        }
      }

      // Published before _Process's passes measure anything, because Drain is only ever called from the top of
      // _Process - so a chunk installed this frame is tuned this frame, and one streaming in a hundred metres
      // away never draws the frame of full-density grass that tuning it here used to prevent.
      _patches[key] = patches;
    }

    /// <summary>
    /// Scatters as many queued chunks as this frame's budget allows, nearest to the camera first.
    /// </summary>
    /// <remarks>
    /// Nearest first because the queue is longest at login, when the whole view volume arrives at once, and
    /// the order it drains in is the order the field appears in. Ground under the player's feet is what they
    /// are looking at; ground out at the fade radius is a handful of clumps whenever it gets there.
    /// </remarks>
    private void Drain(Vector3? eye)
    {
      if (_pending.Count == 0)
      {
        return;
      }

      var budget = Mathf.Max(BuildBudgetMillis, 0.1f);
      var watch = Stopwatch.StartNew();

      while (_pending.Count > 0 && watch.Elapsed.TotalMilliseconds < budget)
      {
        var next = Nearest(eye);
        var terrain = _pending[next];
        _pending.Remove(next);

        Raise(next, terrain);
      }
    }

    /// <summary>The queued chunk closest to the camera, or an arbitrary one when there is no camera.</summary>
    /// <remarks>
    /// A scan rather than a structure kept in order. The queue is at most the view volume - a couple of
    /// hundred entries at login and none once it has settled - and this runs a handful of times a frame, so a
    /// heap maintained across every install would cost more than this costs to walk.
    /// </remarks>
    private ChunkKey Nearest(Vector3? eye)
    {
      var best = default(ChunkKey);
      var bestDistance = float.MaxValue;
      var first = true;

      foreach (var (key, terrain) in _pending)
      {
        if (first)
        {
          best = key;
          first = false;
        }

        if (!eye.HasValue || terrain.Vertices.Length == 0)
        {
          break;
        }

        // The first vertex rather than the surface's middle: a chunk is 32 m across and this is ordering the
        // queue, not measuring it, so any point inside one separates the near from the far.
        var distance = terrain.Vertices[0].DistanceSquaredTo(eye.Value);
        if (distance < bestDistance)
        {
          bestDistance = distance;
          best = key;
        }
      }

      return best;
    }

    /// <summary>Turns one cell's transforms into a multimesh under this node.</summary>
    private Patch Install(ChunkKey key, long cell, List<Transform3D> transforms, Layer layer)
    {
      var tuft = layer == Layer.Tuft;

      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = tuft ? _tuft : _clump,
        InstanceCount = transforms.Count
      };

      // Filled through the buffer rather than by SetInstanceTransform per instance, which is one call across
      // the managed boundary each: at a couple of thousand clumps a chunk and two chunks installed a frame,
      // that was the single most expensive thing about streaming into a meadow. Same data, one call.
      //
      // The layout is the 3x4 matrix in row-major order - three basis rows of three, each followed by one
      // component of the origin. `Basis`'s C# indexer addresses *columns*, so the row-major read is
      // transposed: element [row, column] is `basis[column][row]`.
      var buffer = new float[transforms.Count * 12];

      var min = transforms[0].Origin;
      var max = min;

      for (var i = 0; i < transforms.Count; i++)
      {
        var basis = transforms[i].Basis;
        var origin = transforms[i].Origin;
        var at = i * 12;

        buffer[at + 0] = basis[0].X;
        buffer[at + 1] = basis[1].X;
        buffer[at + 2] = basis[2].X;
        buffer[at + 3] = origin.X;
        buffer[at + 4] = basis[0].Y;
        buffer[at + 5] = basis[1].Y;
        buffer[at + 6] = basis[2].Y;
        buffer[at + 7] = origin.Y;
        buffer[at + 8] = basis[0].Z;
        buffer[at + 9] = basis[1].Z;
        buffer[at + 10] = basis[2].Z;
        buffer[at + 11] = origin.Z;

        min = min.Min(origin);
        max = max.Max(origin);
      }

      multi.Buffer = buffer;

      // The tallest this layer's plants stand above the ground they were placed on. Both meshes are authored
      // standing on y = 0, so this is the whole of what the transforms do not already record.
      var top = (tuft ? TuftHeight : Height) * (1.0f + HeightSpread);

      var node = new MultiMeshInstance3D
      {
        Name = $"Grass_{layer}_{key.X}_{key.Y}_{cell}",
        Multimesh = multi,
        MaterialOverride = tuft ? _tuftMaterial : _material,
        CastShadow = GeometryInstance3D.ShadowCastingSetting.Off,

        // The wind's margin, plus however far the coverage compensation can grow a plant past the transforms
        // Godot measured the bounds from. Without the second term a thinned cell pops out of view while its
        // now much taller plants are still on screen.
        ExtraCullMargin = WindCullMargin + top * (Mathf.Max(MaxCoverageScale, 1.0f) - 1.0f)
      };

      AddChild(node);

      return new Patch
      {
        Node = node,
        Multi = multi,
        Min = min,

        // Grown by the tallest plant, because the transforms only record where each one stands. A box that
        // stopped at the ground would call a cell below the camera further away than its blades are.
        Max = max + new Vector3(0.0f, top, 0.0f),
        Total = transforms.Count,
        Layer = layer
      };
    }

    /// <summary>
    /// Places clumps over the grassy part of one chunk's surface, grouped by cell and shuffled within each.
    /// </summary>
    /// <remarks>
    /// Area-weighted per triangle and slot-weighted per triangle, with the fraction left over carried into the
    /// next one. The carry is what makes a low density work at all: a triangle of a quarter of a square metre
    /// wants 0.6 of a clump, and rounding that to zero everywhere would leave thin ground bare no matter how
    /// much of it there was.
    ///
    /// <para>
    /// <see cref="BlockAppearance.SurfaceSlot.DryGrass"/> is deliberately not included, though bunchgrass is
    /// still grass. It is a different colour, and this draws one material - so a dune would come out the green
    /// of a meadow. Giving it its own tint is a per-instance colour on the multimesh and a <c>COLOR</c> read in
    /// the shader, which is worth doing and is not done here.
    /// </para>
    /// </remarks>
    private Dictionary<long, List<Transform3D>>[] Scatter(
      ChunkKey key, Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      var cells = new Dictionary<long, List<Transform3D>>[Layers];
      for (var layer = 0; layer < Layers; layer++)
      {
        cells[layer] = new Dictionary<long, List<Transform3D>>();
      }

      // Seeded from the chunk alone - not from the mesh - so that a re-mesh, a reconnect or a second client
      // standing in the same field all scatter the same grass. Mixed rather than concatenated because
      // neighbouring chunks differ in one low bit, and a xorshift fed adjacent seeds starts out correlated.
      var rng = new Rng(Mix((uint)(key.X * 73856093) ^ (uint)(key.Y * 19349663) ^ (uint)(key.Z * 83492791)));

      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var edge = Mathf.Max(CellMetres, 0.5f);

      // Per layer, and interleaved rather than run as two passes over the index buffer: one walk costs what it
      // costs, and the carry below only works if a layer sees every triangle in order.
      var carry = new float[Layers];
      var density = new float[Layers];
      var height = new float[Layers];
      var natural = new float[Layers];

      density[(int)Layer.Clump] = Density;
      height[(int)Layer.Clump] = Height;
      natural[(int)Layer.Clump] = ClumpNaturalHeight;

      // Zero density is how a failed tuft load turns into a clump-only field, with nothing else to check.
      density[(int)Layer.Tuft] = _tuft == null ? 0.0f : TuftDensity;
      height[(int)Layer.Tuft] = TuftHeight;
      natural[(int)Layer.Tuft] = TuftNaturalHeight;

      // Two passes, because the cap has to be applied as a density and not as a stopping point. Walking until
      // MaxPerChunk plants had been placed would fill the chunk in the order the mesher emitted its triangles
      // and then stop, which leaves one side of a capped chunk bare - a far more visible failure than the
      // uniformly thinner grass that scaling gives, and one that looks like a bug in the scatter rather than
      // like a budget.
      var grassy = GrassyArea(vertices, normals, indices, weights);

      if (grassy <= 0.0f)
      {
        return cells;
      }

      // Shared across the layers, because MaxPerChunk bounds the memory one chunk's buffers take and that is a
      // property of the chunk rather than of either layer. Scaled by the same factor so a capped chunk keeps
      // the mix it was tuned with instead of losing whichever layer is checked second.
      var asked = grassy * (density[0] + density[1]);
      if (asked > MaxPerChunk)
      {
        var shrink = MaxPerChunk / asked;
        density[0] *= shrink;
        density[1] *= shrink;
      }

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        // The vertex normals rather than the face's own cross product, because the sign of that depends on a
        // winding this code should not have to know, and getting it backwards would grow grass on the
        // undersides of overhangs and nowhere else.
        var upright = (normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f;
        if (upright < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        var ab = vertices[ib] - a;
        var ac = vertices[ic] - a;

        var area = ab.Cross(ac).Length() * 0.5f;
        if (area <= 0.0f)
        {
          continue;
        }

        for (var layer = 0; layer < Layers; layer++)
        {
          carry[layer] += area * grass * density[layer];

          var wanted = (int)carry[layer];
          carry[layer] -= wanted;

          for (var n = 0; n < wanted; n++)
          {
            // Uniform over the triangle: the two rolls address the enclosing parallelogram, and folding the
            // far half back over the diagonal is what keeps it uniform rather than piling points into one
            // corner.
            var u = rng.Float();
            var v = rng.Float();
            if (u + v > 1.0f)
            {
              u = 1.0f - u;
              v = 1.0f - v;
            }

            var at = a + ab * u + ac * v;

            var grown = height[layer] * (1.0f + (rng.Float() - 0.5f) * 2.0f * HeightSpread);
            var scale = grown / natural[layer];

            // Uniform, so the basis stays a rotation times a scalar - which is the assumption grass.gdshader
            // inverts the model matrix under. Grass is drawn straight up whatever it grows on, rather than
            // along the ground's normal: a plant on a slope grows towards the sky, not out of the hill
            // sideways.
            var basis = new Basis(Vector3.Up, rng.Float() * Mathf.Tau).Scaled(new Vector3(scale, scale, scale));

            var cell = CellOf(at, edge);
            if (!cells[layer].TryGetValue(cell, out var list))
            {
              list = new List<Transform3D>();
              cells[layer][cell] = list;
            }

            list.Add(new Transform3D(basis, at));
          }
        }
      }

      // Shuffled so that VisibleInstanceCount's prefix is a subset of the whole cell rather than of whichever
      // triangles the mesher emitted first. Without this, halving the count on a distant cell would empty one
      // side of it and leave the other at full density.
      for (var layer = 0; layer < Layers; layer++)
      {
        foreach (var transforms in cells[layer].Values)
        {
          for (var i = transforms.Count - 1; i > 0; i--)
          {
            var j = (int)(rng.Next() % (uint)(i + 1));
            (transforms[i], transforms[j]) = (transforms[j], transforms[i]);
          }
        }
      }

      return cells;
    }

    /// <summary>
    /// Square metres of this surface that can carry grass, each weighted by how much grass it is.
    /// </summary>
    /// <remarks>
    /// Exactly the quantity <see cref="Scatter"/>'s main loop multiplies by the density, summed ahead of time
    /// so the cap can be turned into a density before any of it is spent. Another pass over the index buffer
    /// and nothing else - no allocation, no random numbers - against a loop that is already walking it.
    /// </remarks>
    private float GrassyArea(Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var total = 0.0f;

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        if ((normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        total += (vertices[ib] - a).Cross(vertices[ic] - a).Length() * 0.5f * grass;
      }

      return total;
    }

    /// <summary>Which world-aligned cell a point falls in, packed into one key.</summary>
    private static long CellOf(Vector3 at, float edge)
    {
      var x = Mathf.FloorToInt(at.X / edge);
      var z = Mathf.FloorToInt(at.Z / edge);

      return ((long)x << 32) ^ (uint)z;
    }

    /// <summary>
    /// Retunes every cell's instance count and clump size for where the player and the camera are now.
    /// </summary>
    /// <remarks>
    /// Two passes, and the second one is the work the single pass used to do. The first exists only to total
    /// what every cell is asking for, because <see cref="MaxVisibleInstances"/> is a budget across the whole
    /// field and cannot be spent until it is known how many are competing for it. Each cell's share is kept on
    /// its own <see cref="Patch.Fraction"/> in between, so no distance is measured twice and nothing is
    /// allocated to pair the passes up.
    ///
    /// <para>
    /// Cheap enough to run unconditionally: a subtraction and a rounding per cell, with both assignments skipped
    /// unless the value actually changed - which, at a metre or so of movement, they mostly do not. A cell
    /// outside the fade radius settles on a count of zero and a scale of one and is then never written to again,
    /// which is most of what the terrain holds.
    /// </para>
    /// </remarks>
    public override void _Process(double delta)
    {
      var eye = Eye();

      Drain(eye);

      if (_patches.Count == 0 || !eye.HasValue)
      {
        return;
      }

      // The eye is the fallback for the same reason Eye() may be null at all: this node is built before the
      // scene has finished coming up, so the first frames can arrive with no player to measure from. Falling
      // back to the old behaviour draws the field slightly wrong for a frame; refusing to draw it does not.
      var focus = _focus ?? eye.Value;

      var band = GrassLod.BandScale(focus.DistanceTo(eye.Value), ReferenceZoomMetres, ZoomResponse, MaxZoomScale);

      // A band each, which is what makes the two meshes a cross-fade rather than a swap at a radius: the clumps
      // thin out over their own shorter taper while the tufts are still at full density, so the mix shifts
      // gradually and no plant ever changes height.
      var clumpFull = FullDensityMetres * band;
      var clumpFade = FadeOutMetres * band;
      var tuftFull = TuftFullDensityMetres * band;
      var tuftFade = TuftFadeOutMetres * band;

      var wanted = 0;

      foreach (var patches in _patches.Values)
      {
        foreach (var patch in patches)
        {
          var tuft = patch.Layer == Layer.Tuft;

          patch.Fraction = GrassLod.FractionAt(
            GrassLod.DistanceToBox(focus, patch.Min, patch.Max),
            tuft ? tuftFull : clumpFull,
            tuft ? tuftFade : clumpFade);

          wanted += Mathf.RoundToInt(patch.Total * patch.Fraction);
        }
      }

      var trim = GrassLod.BudgetTrim(wanted, MaxVisibleInstances);

      foreach (var patches in _patches.Values)
      {
        foreach (var patch in patches)
        {
          Retune(patch, trim);
        }
      }
    }

    /// <summary>Sets one cell's instance count and clump size from the share it was given this frame.</summary>
    private void Retune(Patch patch, float trim)
    {
      var fraction = patch.Fraction * trim;
      var visible = Mathf.RoundToInt(patch.Total * fraction);

      if (visible != patch.Visible)
      {
        patch.Visible = visible;
        patch.Multi.VisibleInstanceCount = visible;

        // Hidden rather than merely emptied. A multimesh with zero visible instances still costs its place in
        // the culling pass, and beyond the fade radius that is most of what the terrain holds.
        patch.Node.Visible = visible > 0;
      }

      // Fed the *trimmed* fraction, so what the budget takes away in count it gives back in clump size for as
      // long as MaxCoverageScale has headroom.
      var scale = GrassLod.CoverageScale(fraction, CoverageCompensation, MaxCoverageScale);

      // Compared against a threshold rather than for equality. A distance that changes smoothly makes a float
      // that never repeats exactly, so an exact compare would push a uniform every frame for every visible cell
      // to move a clump by a fraction of a millimetre.
      if (Mathf.Abs(scale - patch.Scale) > ScaleEpsilon)
      {
        patch.Scale = scale;

        // The shader adds this to 1, so an instance nobody sets is an instance at its authored size - which is
        // what every ground-cover prop StaticEntityRenderer draws with this same shader relies on.
        patch.Node.SetInstanceShaderParameter(ExtraScaleParameter, scale - 1.0f);
      }
    }

    /// <summary>
    /// Where the camera is, or null if there is not one yet.
    /// </summary>
    /// <remarks>
    /// Null is a real state and not only a test's: this node is built by <c>game.gd</c> before the scene has
    /// finished coming up, so the first chunks can be installed with no camera to measure against. It leaves a
    /// cell at its full count rather than at none, because the two failures are not comparable - a frame or two
    /// of grass drawn too densely is a frame or two of grass, and grass that defaults to hidden and never hears
    /// otherwise is a feature that silently does nothing.
    /// </remarks>
    private Vector3? Eye()
    {
      var camera = GetViewport()?.GetCamera3D();

      return camera == null ? null : camera.GlobalPosition;
    }

    public void Remove(ChunkKey key)
    {
      _pending.Remove(key);

      if (!_patches.Remove(key, out var patches))
      {
        return;
      }

      foreach (var patch in patches)
      {
        if (IsInstanceValid(patch.Node))
        {
          patch.Node.QueueFree();
        }
      }
    }

    public void Clear()
    {
      _pending.Clear();

      foreach (var key in new List<ChunkKey>(_patches.Keys))
      {
        Remove(key);
      }
    }

    /// <summary>Loads the mesh and material once. False if the mesh could not be loaded.</summary>
    /// <remarks>
    /// A failure is remembered, not retried. The alternative is a file-not-found for every chunk that streams
    /// in, which is the log for the rest of the session.
    /// </remarks>
    private bool Load()
    {
      if (_loaded)
      {
        return _clump != null;
      }

      _loaded = true;

      _clump = ResourceLoader.Load<Godot.Mesh>(ClumpPath);
      if (_clump == null)
      {
        GD.PushError($"[grass] {ClumpPath} did not load; the ground will have no grass on it.");
        return false;
      }

      // A missing tuft is survivable where a missing clump is not: the field comes out as the clump layer
      // alone, which is thinner and dearer but is still a field. Scatter skips a layer with no mesh.
      _tuft = ResourceLoader.Load<Godot.Mesh>(TuftPath);
      if (_tuft == null)
      {
        GD.PushError($"[grass] {TuftPath} did not load; the field will be clumps only.");
      }

      var authored = ResourceLoader.Load<ShaderMaterial>(MaterialPath);
      if (authored == null)
      {
        GD.PushError($"[grass] {MaterialPath} did not load; the grass keeps the mesh's own material.");
        return true;
      }

      _material = authored;

      // Its own copy, with the one flag that is a fact about the mesh flipped - the same duplicate-and-set
      // StaticEntityRenderer.MaterialFor does per kind, and for the same reason: `uv_v_at_tip` is a material
      // uniform, so two meshes unwrapped in opposite directions cannot share one. One duplicate for the whole
      // field, so the tufts are still a single material however many cells carry them.
      var tuftMaterial = (ShaderMaterial)authored.Duplicate();
      tuftMaterial.SetShaderParameter(UvVAtTip, true);
      _tuftMaterial = tuftMaterial;

      return true;
    }

    /// <summary>
    /// The scatter's random source: xorshift32, inlined so a chunk's worth of rolls allocates nothing.
    /// </summary>
    /// <remarks>
    /// Not <see cref="RandomNumberGenerator"/>, and not <c>System.Random</c>. What is wanted here is that the
    /// same chunk scatters the same grass on every client and after every re-mesh, forever - and neither of
    /// those documents its sequence as stable across versions. Six lines that do is the cheaper guarantee.
    /// </remarks>
    private struct Rng
    {
      private uint _state;

      internal Rng(uint seed)
      {
        _state = seed == 0u ? 0x9E3779B9u : seed;
      }

      internal uint Next()
      {
        _state ^= _state << 13;
        _state ^= _state >> 17;
        _state ^= _state << 5;

        return _state;
      }

      /// <summary>A float in [0, 1). The top 24 bits, because xorshift's low ones are the weakest.</summary>
      internal float Float() => (Next() >> 8) * (1.0f / 16777216.0f);
    }

    /// <summary>Avalanches a seed, so that chunks whose keys differ in one bit do not scatter alike.</summary>
    private static uint Mix(uint x)
    {
      x ^= x >> 16;
      x *= 0x7FEB352Du;
      x ^= x >> 15;
      x *= 0x846CA68Bu;
      x ^= x >> 16;

      return x;
    }
  }
}
