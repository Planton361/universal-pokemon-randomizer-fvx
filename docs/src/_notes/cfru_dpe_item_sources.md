# CFRU/DPE Item Sources

Status: diagnostic-only source map for CFRU/DPE Gen9 BPRE item lists, item pools, and mechanic-item exclusion coverage.
No randomizer behavior, GUI, RNQS, settings-profile, script, gift, or NPC item logic is changed by this note.

Codex did not run, copy, generate, modify, or inspect ROMs for this note.

## Scope

This diagnosis answers whether UPR-FVX already has a source-backed CFRU/DPE item list that can drive Mega Stone,
Z-Crystal, Dynamax, and Gigantamax item exclusions.

The code/resource search covered:

- `ITEM_`
- `Mega Stone`
- `Z_CRYSTAL`
- `Z Crystal`
- `Dynamax`
- `Gigantamax`
- `Pidgeotite`
- `Cameruptite`
- `Snorlium`
- `Necrozium`
- `Ultranecrozium`

The search was limited to repository files and non-ROM source code.

## UPR-FVX Item Model

`Item` is intentionally small. It stores:

- standard item `id`
- display `name`
- `allowed`
- `bad`
- `tm`
- passive mechanic categories

`Item` does not store item descriptions. Text such as "Z-Power", "Z-Move", "Ultra Burst", "Mega Evolution",
"Dynamax", or "Gigantamax" is therefore not available to non-ROM predicates through the current item model.

The shared `RomHandler` item API exposes:

- all loaded items
- allowed items
- non-bad items
- evolution items
- X items
- Mega Stones
- regular/OP shop helper sets
- field item get/set
- shop get/set
- pickup get/set
- in-game trade get/set

`AbstractRomHandler.getAllowedItems()` filters loaded items by `Item.isAllowed()`. `getNonBadItems()` additionally
filters out `Item.isBad()`. These are generic item-quality filters, not mechanic filters.

## Gen3 Item Loading

`Gen3RomHandler.loadItems()` reads item names from the ROM `ItemData` table and converts internal Gen3 item IDs to the
UPR-FVX standard item ID space with `Gen3Constants.itemIDToStandard(...)`.

For CFRU/DPE Gen9 BPRE, the loader uses the expanded CFRU/DPE item limit when the table looks plausible. The current
constant is:

- `Gen3Constants.cfruDpeItemCount = 799`
- `Gen3Constants.cfruDpeMaxItemID = cfruDpeItemCount - 1`

If an item name cannot be decoded safely, the loader creates a fallback name such as `item #<internal id>` and marks the
item as not allowed and bad.

`Gen3Constants.bannedItems` still carries the baseline Gen3 disallowed items. CFRU/DPE also has
`cfruDpeEncounterHeldItemBannedItems`, which excludes modern special/system/form items from encounter-held item pools.
That list is not a full mechanic category table.

## CFRU/DPE Item Sources Found

No complete CFRU/DPE `ITEM_*` header, `items.h`, or generated item constants table is currently present in this repo.
Exact CFRU/DPE-style constants such as `ITEM_PIDGEOTITE`, `ITEM_CAMERUPTITE`, `ITEM_SNORLIUM_Z`, or
`ITEM_ULTRANECROZIUM_Z` are not available as source-backed repository constants.

The repository does contain partial or derived item sources:

- `ItemIDs` generic later-generation constants, including Mega Stones, Z-Crystals, Dynamax/GMax-related items, and
  Mega accessories.
- Gen6 constants that group known Mega Stone IDs.
- Gen7 constants that group Z-Crystals and later-generation banned/bad item ranges.
- Gen3 CFRU/DPE item count and a few explicit modern special/system item constants/ranges.
- `ItemMechanicPredicates`, which combines generic constants, known CFRU/DPE identity ranges, and normalized item names.

The current CFRU/DPE mechanic ranges used by the predicate are:

- `0x214`: Ultranecrozium/Necrozium Z source identity
- `0x215..0x243`: known CFRU/DPE Mega Stone block
- `0x244..0x265`: known CFRU/DPE Z-Crystal block

Those ranges are useful, but they are not a substitute for a complete CFRU/DPE item constants source. They should be
treated as curated compatibility metadata until a full CFRU/DPE item table is imported or generated.

## Item Names And Descriptions

Item names are loaded from the ROM item table. They can therefore support normalized-name classification after the item
model has been loaded.

Item descriptions are not modeled in `Item`. Description-pattern classification is currently only a diagnostic design
idea, not an implemented non-ROM predicate. A future local audit could read descriptions user-side and flag phrases such
as "Z-Power", "Z-Move", "Ultra Burst", "Mega Evolution", "Dynamax", or "Gigantamax", but Codex should not perform that
ROM-backed audit.

## Mechanic-Filtered Item Pools

The shared helper in `Randomizer` filters item candidates through `ItemMechanicPredicates` using:

- `Settings.includeMegaItems`
- `Settings.includeZCrystalItems`
- `Settings.includeDynamaxGmaxItems`

Mechanic filtering is connected to replacement pools that draw new items:

| Source | Replacement path | Mechanic-filtered |
| --- | --- | --- |
| Field items | `ItemRandomizer.randomizeNonTMFieldItems`, RANDOM/RANDOM_EVEN | yes |
| Shops | `ItemRandomizer.randomizeShopItems`, RANDOM | yes |
| Pickup | `ItemRandomizer.randomizePickupItems`, RANDOM | yes |
| Trainer held items | `TrainerPokemonRandomizer.randomizeHeldItem` draws | yes |
| Starter held items | `StarterRandomizer.randomizeStarterHeldItems` draws | yes |
| Wild held items | `EncounterHeldItemRandomizer.randomizeWildHeldItems` draws | yes |
| Totem/static held item draws | consumable held item draw paths | yes |
| In-game trade held items | `TradeRandomizer.randomizeIngameTrades` random held item path | yes |

The filter does not currently mean "all item appearances in the ROM are removed." It means mechanic items are excluded
from the randomizer replacement candidate pools that use the shared helper.

## Not Mechanic-Filtered Or Not Fully Modeled

The following paths are important caveats for local mechanic-item leaks:

| Source | Current coverage |
| --- | --- |
| Field item SHUFFLE | shuffles existing field items; it does not rebuild from a filtered replacement pool |
| Shop SHUFFLE | shuffles existing shop items; it does not rebuild from a filtered replacement pool |
| Trainer held item preservation | existing Z-Crystals or Mega Stones can be preserved by trainer-specific early returns |
| PC Potion misc tweak | draws from non-bad non-TM items and does not currently use the mechanic filter |
| Generic NPC gifts | no generic Gen3 `giveitem` script gift parser/filter was found |
| Generic script items | no broad script item patcher was found |
| Static one-off item scripts | not covered unless they are modeled as field item balls or hidden item signposts |

Gen3 field item scanning covers item-ball event scripts that match the known simple script shape and hidden signpost
items with signpost types 5 through 7. It does not decode and filter every possible NPC or script-level `giveitem`.

This means a local item such as Necrozium Z, Snorlium Z, Cameruptite, or Pidgeotite can have different causes:

- Predicate gap, if it came from a mechanic-filtered replacement pool and was not classified.
- Shuffle-mode behavior, if it was already in the source list and the mode only moved existing items.
- Preservation behavior, if trainer held item logic intentionally kept an existing Z-Crystal or Mega Stone.
- Unmodeled script/gift/NPC source, if the item came from a non-field-item script path.
- PC Potion path gap, if it came from the Misc Tweak PC Potion replacement.

## Used Lists

UPR-FVX currently uses these lists/tables for item randomization decisions:

- loaded ROM item names from `ItemData`
- `allowed` and `bad` flags on loaded `Item` objects
- static Gen3 banned item lists
- static Gen3 held/consumable/type/sensible held item lists
- shop, pickup, field, trade, trainer, and encounter APIs exposed by `RomHandler`
- generic later-generation constants in `ItemIDs`, Gen6, and Gen7 constants
- curated mechanic categories and CFRU/DPE ranges in `ItemMechanicPredicates`

## Lists Not Currently Present

The repo does not currently contain:

- a complete CFRU/DPE `ITEM_*` constants header
- a complete DPE-generated item category table
- a full CFRU/DPE Mega Stone/Z-Crystal/Dynamax/GMax source table independent of local predicates
- a modeled item description table in `Item`
- a generic script/gift/NPC item-source index

Those missing sources are the main reason repeated local leaks need source diagnosis rather than only one-off item-name
patches.

## Recommended Next Fix Strategy

1. Add an opt-in local item-source audit report that the user runs against their local ROM. The report should list item
   id, item name, mechanic category, source pool, mode, and whether that source is mechanic-filtered.
2. Import or generate a CFRU/DPE item constants metadata file from source-controlled CFRU/DPE/DPE headers outside the
   ROM. Use it to replace hardcoded compatibility ranges where possible.
3. Decide shuffle semantics separately. If disabled mechanic items should disappear from field/shop shuffle modes, that
   is a behavior change and should be implemented in a focused PR.
4. Decide whether PC Potion should mirror mechanic item exclusions. If yes, wire the misc tweak through the shared
   mechanic filter in a focused PR.
5. Keep generic script/gift/NPC items diagnostic-only until an explicit source index exists. Do not patch arbitrary
   scripts blindly.

## Suggested Future Audit Columns

A local user-run report should include:

- item id
- internal item id, if available
- item name
- mechanic category
- source pool: field, shop, pickup, wild held, trainer held, starter held, trade held, script, gift, NPC, or unknown
- randomization mode
- mechanic-filtered yes/no
- included setting that allowed or blocked it

That report would make future leaks actionable by distinguishing predicate gaps from source-coverage gaps.
