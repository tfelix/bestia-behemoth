package net.bestia.zone.cartography.render

/**
 * What a point a player can *use* is drawn as on their map: somewhere to buy, sleep, pray or come back to life.
 *
 * Sent as the enum name, for the reason `MapTileService.Place.poi` is: the set is closed and hand-authored, so
 * the client can translate it, but only if it is told which kind rather than handed an English word.
 *
 * ### A decision per trade, not a default
 *
 * [forBusiness] answers from [BY_BUSINESS], which names every `BusinessCatalogue` trade outright - mapped to a
 * kind, or to null to keep it off the map - and `MapServiceTest` fails the build when a trade is added without
 * an entry. It is the same forcing function [MapVisibility.of] gets from an exhaustive `when`, which a list of
 * strings cannot have.
 *
 * Being listed here is not what puts a trade on the map. Somebody has to keep the shop as well - see
 * `MapPoiIndex` - so a trade nobody staffs yet is decided here in advance and appears the day an occupation
 * takes it on.
 */
enum class MapService {

  /** A counter that sells goods: the general store and the trades that make what they sell. */
  MERCHANT,

  /** A market building. Shown as a place in its own right, whether or not anybody trades in it yet. */
  MARKET,

  INN,
  TEMPLE,

  /** A barracks: where the guard is when it is not on the street. */
  GUARD,

  /** A ward stone at a home settlement. Not a business; it is an entity that can be broken. */
  WARD_STONE;

  companion object {

    /**
     * Every trade's map decision, keyed by `BusinessType.id`.
     *
     * Null means "deliberately not on the map", and most of those are waiting on a kind of their own rather than
     * being secrets: a smith or a healer is somewhere a player wants to find, and folding it into [MERCHANT]
     * would make the one glyph mean two different errands. `market_trader` is null because its stalls stand in
     * market buildings, which are listed as [MARKET] already - a second mark there would double every market.
     */
    val BY_BUSINESS: Map<String, MapService?> = mapOf(
      "baker" to MERCHANT,
      "butcher" to MERCHANT,
      "brewer" to MERCHANT,
      "vintner" to MERCHANT,
      "fishmonger" to MERCHANT,
      "miller" to MERCHANT,
      "general_store" to MERCHANT,
      "inn" to INN,
      "stable" to null,
      "market_trader" to null,
      "shipwright" to MERCHANT,
      "blacksmith" to null,
      "armourer" to null,
      "carpenter" to MERCHANT,
      "mason" to MERCHANT,
      "potter" to MERCHANT,
      "charcoaler" to MERCHANT,
      "tanner" to MERCHANT,
      "dyer" to MERCHANT,
      "weaver" to MERCHANT,
      "tailor" to MERCHANT,
      "temple" to TEMPLE,
      "healer" to null,
      "apothecary" to null,
      "scribe" to null,
      "jeweller" to MERCHANT,
      "bookbinder" to MERCHANT,
      "alchemist" to MERCHANT,
      "banker" to null,
      "barracks" to GUARD,
    )

    /** The kind a trade is drawn as, or null for one kept off the map or one nobody has decided about. */
    fun forBusiness(businessType: String): MapService? {
      return BY_BUSINESS[businessType]
    }
  }
}
