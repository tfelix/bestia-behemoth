package net.bestia.zone.questgen.plan

/** The kinds of real thing a step can point at. Each is found by [QuestWorld]. */
enum class BindSlot {
  /** A townsperson who tells the true legend of the seed's subject. */
  LEGEND_TELLER,

  /** A townsperson whose house remembers how the seed's subject was lost. */
  LOSS_WITNESS,

  /** The named grave of a figure who once held the seed's subject. */
  KEEPER_GRAVE,

  /** The treasure cache the seed's subject lies in. */
  RELIC_CACHE,

  /** The standing town whose temple first kept the seed's subject. */
  HOME_TEMPLE,

  /** The town of the seed and the goods it lacks. */
  TOWN_NEED,

  /** A townsperson who tells the news of the seed, in a town that heard it. */
  NEWS_TELLER,

  /** A townsperson in the town of the seed, which has not heard the news. */
  NEWS_LISTENER,
}
