package com.uprfvx.romio.services;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;

import java.util.Locale;
import java.util.Set;

public final class CfruDpeItemPoolPolicy {

    private static final Set<Integer> FOSSIL_IDS = Set.of(
            ItemIDs.rootFossil,
            ItemIDs.clawFossil,
            ItemIDs.helixFossil,
            ItemIDs.domeFossil,
            ItemIDs.oldAmber,
            ItemIDs.armorFossil,
            ItemIDs.skullFossil,
            ItemIDs.coverFossil,
            ItemIDs.plumeFossil,
            ItemIDs.jawFossil,
            ItemIDs.sailFossil,
            ItemIDs.fossilizedBird,
            ItemIDs.fossilizedFish,
            ItemIDs.fossilizedDrake,
            ItemIDs.fossilizedDino
    );
    private static final Set<String> FOSSIL_NAMES = Set.of(
            "rootfossil",
            "clawfossil",
            "helixfossil",
            "domefossil",
            "oldamber",
            "armorfossil",
            "skullfossil",
            "coverfossil",
            "plumefossil",
            "jawfossil",
            "sailfossil",
            "fossilizedbird",
            "fossilizedfish",
            "fossilizeddrake",
            "fossilizeddino",
            "birdfossil",
            "fishfossil",
            "drakefossil",
            "dinofossil"
    );
    private static final Set<String> POKE_BALL_NAMES = Set.of(
            "masterball",
            "ultraball",
            "greatball",
            "pokeball",
            "safariball",
            "netball",
            "diveball",
            "nestball",
            "repeatball",
            "timerball",
            "luxuryball",
            "premierball"
    );
    private static final Set<Integer> USEFUL_BERRY_IDS = Set.of(
            ItemIDs.cheriBerry,
            ItemIDs.chestoBerry,
            ItemIDs.pechaBerry,
            ItemIDs.rawstBerry,
            ItemIDs.aspearBerry,
            ItemIDs.leppaBerry,
            ItemIDs.oranBerry,
            ItemIDs.persimBerry,
            ItemIDs.lumBerry,
            ItemIDs.sitrusBerry
    );
    private static final Set<Integer> SHARD_EXCHANGE_IDS = Set.of(
            ItemIDs.redShard,
            ItemIDs.blueShard,
            ItemIDs.yellowShard,
            ItemIDs.greenShard
    );
    private static final Set<String> SHARD_EXCHANGE_NAMES = Set.of(
            "redshard",
            "blueshard",
            "yellowshard",
            "greenshard"
    );
    private static final Set<Integer> HIGH_VALUE_VALUABLE_IDS = Set.of(
            ItemIDs.balmMushroom,
            ItemIDs.rareBone,
            ItemIDs.pearlString,
            ItemIDs.cometShard,
            ItemIDs.bigNugget,
            ItemIDs.relicCopper,
            ItemIDs.relicSilver,
            ItemIDs.relicGold,
            ItemIDs.relicVase,
            ItemIDs.relicBand,
            ItemIDs.relicStatue,
            ItemIDs.relicCrown
    );
    private static final Set<String> HIGH_VALUE_VALUABLE_NAMES = Set.of(
            "balmmushroom",
            "rarebone",
            "pearlstring",
            "cometshard",
            "bignugget",
            "reliccopper",
            "relicsilver",
            "relicgold",
            "relicvase",
            "relicband",
            "relicstatue",
            "reliccrown"
    );
    private static final Set<Integer> APRICORN_IDS = Set.of(
            ItemIDs.blackApricorn,
            ItemIDs.blueApricorn,
            ItemIDs.greenApricorn,
            ItemIDs.pinkApricorn,
            ItemIDs.redApricorn,
            ItemIDs.whiteApricorn,
            ItemIDs.yellowApricorn,
            CfruDpeItemCategories.standardIdForSourceId(0x266),
            CfruDpeItemCategories.standardIdForSourceId(0x267),
            CfruDpeItemCategories.standardIdForSourceId(0x268),
            CfruDpeItemCategories.standardIdForSourceId(0x269),
            CfruDpeItemCategories.standardIdForSourceId(0x26A),
            CfruDpeItemCategories.standardIdForSourceId(0x26B),
            CfruDpeItemCategories.standardIdForSourceId(0x26C)
    );
    private static final Set<String> APRICORN_NAMES = Set.of(
            "blackapricorn",
            "blkapricorn",
            "blueapricorn",
            "bluapricorn",
            "greenapricorn",
            "grnapricorn",
            "pinkapricorn",
            "redapricorn",
            "whiteapricorn",
            "whtapricorn",
            "yellowapricorn",
            "ylwapricorn",
            "blackaprikoko",
            "blkaprikoko",
            "blueaprikoko",
            "bluaprikoko",
            "greenaprikoko",
            "grnaprikoko",
            "pinkaprikoko",
            "redaprikoko",
            "whiteaprikoko",
            "whtaprikoko",
            "yellowaprikoko",
            "ylwaprikoko"
    );
    private static final Set<Integer> CHARM_FLUTE_SYSTEM_IDS = Set.of(
            ItemIDs.shinyCharm,
            ItemIDs.ovalCharm,
            ItemIDs.magmaStone,
            ItemIDs.redFlute,
            ItemIDs.blueFlute,
            ItemIDs.blackFlute,
            ItemIDs.whiteFlute,
            ItemIDs.yellowFlute
    );
    private static final Set<String> CHARM_FLUTE_SYSTEM_NAMES = Set.of(
            "shinycharm",
            "ovalcharm",
            "magmastone",
            "redflute",
            "blueflute",
            "blackflute",
            "whiteflute",
            "yellowflute"
    );
    private static final Set<Integer> REVIEW_GAP_NORMAL_POOL_BANNED_IDS = Set.of(
            ItemIDs.sacredAsh,
            CfruDpeItemCategories.standardIdForSourceId(45),
            ItemIDs.rainbowWing,
            CfruDpeItemCategories.standardIdForSourceId(0x1D8),
            ItemIDs.silverWing,
            CfruDpeItemCategories.standardIdForSourceId(0x1D9),
            ItemIDs.lightStone,
            CfruDpeItemCategories.standardIdForSourceId(0x1DB),
            ItemIDs.darkStone,
            CfruDpeItemCategories.standardIdForSourceId(0x1DC),
            ItemIDs.sunFlute,
            CfruDpeItemCategories.standardIdForSourceId(0x1DD),
            ItemIDs.moonFlute,
            CfruDpeItemCategories.standardIdForSourceId(0x1DE),
            ItemIDs.rustedSword,
            CfruDpeItemCategories.standardIdForSourceId(0x1E5),
            ItemIDs.rustedShield,
            CfruDpeItemCategories.standardIdForSourceId(0x1E6),
            ItemIDs.oddKeystone,
            CfruDpeItemCategories.standardIdForSourceId(0x27E),
            ItemIDs.bottleCap,
            CfruDpeItemCategories.standardIdForSourceId(0x27F),
            ItemIDs.goldBottleCap,
            CfruDpeItemCategories.standardIdForSourceId(0x280),
            CfruDpeItemCategories.standardIdForSourceId(0x2E0)
    );
    private static final Set<String> REVIEW_GAP_NORMAL_POOL_BANNED_NAMES = Set.of(
            "sacredash",
            "rainbowwing",
            "silverwing",
            "lightstone",
            "darkstone",
            "sunflute",
            "moonflute",
            "rustedsword",
            "rustedshield",
            "oddkeystone",
            "bottlecap",
            "goldbottlecap",
            "gimmicoin",
            "gimmighoulcoin"
    );
    private static final Set<Integer> HELD_BATTLE_ITEM_IDS_ALLOWED_BY_POLICY = Set.of(
            ItemIDs.lightBall,
            ItemIDs.soulDew,
            ItemIDs.luckyPunch,
            ItemIDs.metalPowder,
            ItemIDs.thickClub,
            ItemIDs.leek
    );

    private CfruDpeItemPoolPolicy() {
    }

    public static boolean isBannedFromNormalItemPools(Item item) {
        return isFossilItem(item) || isReviewGapNormalPoolBannedItem(item);
    }

    public static boolean isBadWhenBanBadItems(Item item) {
        return isFormChangeItem(item) || isShardExchangeItem(item) || isHighValueValuableItem(item)
                || isApricornItem(item) || isCharmFluteSystemItem(item);
    }

    public static boolean isAllowedWhenBanBadItems(Item item) {
        if (item == null) {
            return false;
        }
        return isPokeBallItem(item)
                || USEFUL_BERRY_IDS.contains(item.getId())
                || HELD_BATTLE_ITEM_IDS_ALLOWED_BY_POLICY.contains(item.getId());
    }

    public static boolean isFormChangeItem(Item item) {
        return CfruDpeItemCategories.isArceusPlate(item)
                || CfruDpeItemCategories.isGenesectDrive(item)
                || CfruDpeItemCategories.isSilvallyMemory(item)
                || CfruDpeItemCategories.isNectarOrFormChangeItem(item);
    }

    public static boolean isFossilItem(Item item) {
        if (item == null) {
            return false;
        }
        return FOSSIL_IDS.contains(item.getId()) || FOSSIL_NAMES.contains(normalizedName(item));
    }

    public static boolean isPokeBallItem(Item item) {
        if (item == null) {
            return false;
        }
        return (item.getId() >= ItemIDs.masterBall && item.getId() <= ItemIDs.premierBall)
                || POKE_BALL_NAMES.contains(normalizedName(item));
    }

    public static boolean isShardExchangeItem(Item item) {
        if (item == null) {
            return false;
        }
        return SHARD_EXCHANGE_IDS.contains(item.getId()) || SHARD_EXCHANGE_NAMES.contains(normalizedName(item));
    }

    public static boolean isHighValueValuableItem(Item item) {
        if (item == null) {
            return false;
        }
        return HIGH_VALUE_VALUABLE_IDS.contains(item.getId())
                || HIGH_VALUE_VALUABLE_NAMES.contains(normalizedName(item));
    }

    public static boolean isApricornItem(Item item) {
        if (item == null) {
            return false;
        }
        return APRICORN_IDS.contains(item.getId()) || APRICORN_NAMES.contains(normalizedName(item));
    }

    public static boolean isCharmFluteSystemItem(Item item) {
        if (item == null) {
            return false;
        }
        return CHARM_FLUTE_SYSTEM_IDS.contains(item.getId())
                || CHARM_FLUTE_SYSTEM_NAMES.contains(normalizedName(item));
    }

    public static boolean isReviewGapNormalPoolBannedItem(Item item) {
        if (item == null) {
            return false;
        }
        return REVIEW_GAP_NORMAL_POOL_BANNED_IDS.contains(item.getId())
                || REVIEW_GAP_NORMAL_POOL_BANNED_NAMES.contains(normalizedName(item));
    }

    private static String normalizedName(Item item) {
        String name = item.getName();
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
