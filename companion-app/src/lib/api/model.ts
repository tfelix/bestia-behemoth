/**
 * The api-server's resources, as named in its OpenAPI document. `npm run gen:api` regenerates schema.d.ts from
 * a running api-server; these names keep the rest of the app off its generated paths.
 */
import type { components } from './schema';

type Schemas = components['schemas'];

export type MasterSummary = Schemas['MasterSummary'];
export type MasterSheet = Schemas['MasterSheet'];
export type Bestia = Schemas['Bestia'];
export type Holder = Schemas['Holder'];
export type Slot = Schemas['Slot'];
export type PartyView = Schemas['PartyView'];
export type GuildView = Schemas['GuildView'];
export type Catalog = Schemas['Catalog'];
export type MapMeta = Schemas['MapMeta'];
export type Place = Schemas['Place'];
export type Service = Schemas['Service'];
export type PlaceDetail = Schemas['PlaceDetail'];
export type StanceOrder = Schemas['StanceOrder'];
export type ChatOrder = Schemas['ChatOrder'];
export type Preferences = Schemas['Preferences'];
export type CompendiumEntry = Schemas['CompendiumEntry'];
export type Position = Schemas['Position'];
export type Journey = Schemas['Journey'];
export type JourneyOrder = Schemas['JourneyOrder'];

export const STANCES = ['HOLD', 'PATROL', 'FORAGE', 'DEFEND'] as const;
export type Stance = (typeof STANCES)[number];
