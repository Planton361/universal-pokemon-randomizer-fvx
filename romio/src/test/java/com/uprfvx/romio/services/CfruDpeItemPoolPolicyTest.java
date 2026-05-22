package com.uprfvx.romio.services;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfruDpeItemPoolPolicyTest {

    @Test
    public void formChangeItemsAreBadOnlyWhenBanBadItemsIsEnabled() {
        assertBadOnly(item(ItemIDs.flamePlate, "Flame Plate"));
        assertBadOnly(item(ItemIDs.burnDrive, "Burn Drive"));
        assertBadOnly(item(ItemIDs.bugMemory, "Bug Memory"));
        assertBadOnly(item(ItemIDs.flyingMemory, "Flying Mem."));
        assertBadOnly(item(9000, "Flying Memory"));
        assertBadOnly(item(9001, "Fire Mem."));
        assertBadOnly(item(9002, "Electr Mem."));
        assertBadOnly(item(9003, "Fight Mem."));
        assertBadOnly(item(ItemIDs.redNectar, "Red Nectar"));
        assertBadOnly(item(ItemIDs.gracidea, "Gracidea"));
        assertBadOnly(cfruDpeItem(0x1DF, "Gracidea"));
        assertBadOnly(item(ItemIDs.revealGlass, "Reveal Glass"));
        assertBadOnly(item(ItemIDs.dNASplicersFuse, "DNA Splicers"));
        assertBadOnly(item(ItemIDs.reinsofUnity, "Reins Unity"));
    }

    @Test
    public void fossilsAreBannedFromNormalItemPools() {
        assertBanned(item(ItemIDs.helixFossil, "Helix Fossil"));
        assertBanned(item(ItemIDs.rootFossil, "Root Fossil"));
        assertBanned(item(ItemIDs.fossilizedBird, "Fossilized Bird"));
        assertBanned(item(ItemIDs.fossilizedFish, "Fish Fossil"));
        assertBanned(item(9000, "Plume Fossil"));
        assertBanned(item(9001, "Old Amber"));
        assertBanned(item(9002, "Fish Fossil"));
    }

    @Test
    public void reviewGapSystemItemsAreBannedFromNormalItemPools() {
        assertBanned(item(ItemIDs.sacredAsh, "Sacred Ash"));
        assertBanned(cfruDpeItem(45, "Sacred Ash"));
        assertBanned(item(ItemIDs.rainbowWing, "Rainbow Wing"));
        assertBanned(cfruDpeItem(0x1D8, "Rainbow Wing"));
        assertBanned(item(ItemIDs.silverWing, "Silver Wing"));
        assertBanned(cfruDpeItem(0x1D9, "Silver Wing"));
        assertBanned(item(ItemIDs.lightStone, "Light Stone"));
        assertBanned(cfruDpeItem(0x1DB, "Light Stone"));
        assertBanned(item(ItemIDs.darkStone, "Dark Stone"));
        assertBanned(cfruDpeItem(0x1DC, "Dark Stone"));
        assertBanned(item(ItemIDs.sunFlute, "Sun Flute"));
        assertBanned(cfruDpeItem(0x1DD, "Sun Flute"));
        assertBanned(item(ItemIDs.moonFlute, "Moon Flute"));
        assertBanned(cfruDpeItem(0x1DE, "Moon Flute"));
        assertBanned(item(ItemIDs.rustedSword, "Rusted Sword"));
        assertBanned(cfruDpeItem(0x1E5, "Rusted Sword"));
        assertBanned(item(ItemIDs.rustedShield, "Rusted Shield"));
        assertBanned(cfruDpeItem(0x1E6, "Rusted Shield"));
        assertBanned(item(ItemIDs.oddKeystone, "Odd Keystone"));
        assertBanned(cfruDpeItem(0x27E, "Odd Keystone"));
        assertBanned(item(ItemIDs.bottleCap, "Bottle Cap"));
        assertBanned(cfruDpeItem(0x27F, "Bottle Cap"));
        assertBanned(item(ItemIDs.goldBottleCap, "Gold Bottle Cap"));
        assertBanned(cfruDpeItem(0x280, "Gold Bottle Cap"));
        assertBanned(cfruDpeItem(0x2E0, "Gimmi Coin"));
        assertBanned(item(9000, "Gimmighoul Coin"));
    }

    @Test
    public void shardsAreBadOnlyWhenBanBadItemsIsEnabled() {
        assertBadOnly(item(ItemIDs.redShard, "Red Shard"));
        assertBadOnly(item(ItemIDs.blueShard, "Blue Shard"));
        assertBadOnly(item(ItemIDs.yellowShard, "Yellow Shard"));
        assertBadOnly(item(ItemIDs.greenShard, "Green Shard"));
        assertBadOnly(item(9000, "Green Shard"));
    }

    @Test
    public void highValueValuablesAreBadOnlyWhenBanBadItemsIsEnabled() {
        assertBadOnly(item(ItemIDs.relicCrown, "Relic Crown"));
        assertBadOnly(item(ItemIDs.relicStatue, "Relic Statue"));
        assertBadOnly(item(ItemIDs.relicBand, "Relic Band"));
        assertBadOnly(item(ItemIDs.relicGold, "Relic Gold"));
        assertBadOnly(item(ItemIDs.relicVase, "Relic Vase"));
        assertBadOnly(item(ItemIDs.relicCopper, "Relic Copper"));
        assertBadOnly(item(ItemIDs.relicSilver, "Relic Silver"));
        assertBadOnly(item(ItemIDs.bigNugget, "Big Nugget"));
        assertBadOnly(item(ItemIDs.balmMushroom, "Balm Mushroom"));
        assertBadOnly(item(ItemIDs.pearlString, "Pearl String"));
        assertBadOnly(item(ItemIDs.cometShard, "Comet Shard"));
        assertBadOnly(item(ItemIDs.rareBone, "Rare Bone"));
        assertBadOnly(item(9000, "Relic Crown"));
    }

    @Test
    public void apricornsAreBadOnlyWhenBanBadItemsIsEnabled() {
        assertBadOnly(item(ItemIDs.redApricorn, "Red Apricorn"));
        assertBadOnly(item(ItemIDs.blueApricorn, "Blue Apricorn"));
        assertBadOnly(item(ItemIDs.greenApricorn, "Green Apricorn"));
        assertBadOnly(item(ItemIDs.yellowApricorn, "Yellow Apricorn"));
        assertBadOnly(item(ItemIDs.pinkApricorn, "Pink Apricorn"));
        assertBadOnly(item(ItemIDs.whiteApricorn, "White Apricorn"));
        assertBadOnly(item(ItemIDs.blackApricorn, "Black Apricorn"));
        assertBadOnly(item(CfruDpeItemCategories.standardIdForSourceId(0x266), "Blk Apricorn"));
        assertBadOnly(item(CfruDpeItemCategories.standardIdForSourceId(0x267), "Blu Apricorn"));
        assertBadOnly(item(CfruDpeItemCategories.standardIdForSourceId(0x268), "Grn Apricorn"));
        assertBadOnly(item(CfruDpeItemCategories.standardIdForSourceId(0x26B), "Wht Apricorn"));
        assertBadOnly(item(CfruDpeItemCategories.standardIdForSourceId(0x26C), "Ylw Apricorn"));
        assertBadOnly(item(9000, "Red Aprikoko"));
    }

    @Test
    public void charmFluteAndMagmaSystemItemsAreBadOnlyWhenBanBadItemsIsEnabled() {
        assertBadOnly(item(ItemIDs.shinyCharm, "Shiny Charm"));
        assertBadOnly(item(ItemIDs.ovalCharm, "Oval Charm"));
        assertBadOnly(item(ItemIDs.magmaStone, "Magma Stone"));
        assertBadOnly(item(ItemIDs.redFlute, "Red Flute"));
        assertBadOnly(item(ItemIDs.blueFlute, "Blue Flute"));
        assertBadOnly(item(ItemIDs.blackFlute, "Black Flute"));
        assertBadOnly(item(ItemIDs.whiteFlute, "White Flute"));
        assertBadOnly(item(ItemIDs.yellowFlute, "Yellow Flute"));
    }

    @Test
    public void clearlyAllowedPolicyItemsAreNotNewlyBanned() {
        assertAllowed(item(ItemIDs.potion, "Potion"));
        assertAllowed(item(ItemIDs.superPotion, "Super Potion"));
        assertAllowed(item(ItemIDs.fullRestore, "Full Restore"));
        assertAllowed(item(ItemIDs.antidote, "Antidote"));
        assertAllowed(item(ItemIDs.pokeBall, "Poke Ball"));
        assertAllowed(item(ItemIDs.ultraBall, "Ultra Ball"));
        assertAllowed(item(ItemIDs.masterBall, "Master Ball"));
        assertAllowed(item(ItemIDs.escapeRope, "Escape Rope"));
        assertAllowed(item(ItemIDs.rareCandy, "Rare Candy"));
        assertAllowed(item(ItemIDs.ppUp, "PP Up"));
        assertAllowed(item(ItemIDs.hpUp, "HP Up"));
        assertAllowed(item(ItemIDs.xDefense, "X Defend"));
        assertAllowed(item(ItemIDs.nugget, "Nugget"));
        assertAllowed(item(ItemIDs.leftovers, "Leftovers"));
        assertAllowed(item(ItemIDs.eviolite, "Eviolite"));
        assertAllowed(item(ItemIDs.fireGem, "Fire Gem"));
        assertAllowed(item(ItemIDs.wideLens, "Wide Lens"));
        assertAllowed(item(ItemIDs.throatSpray, "Throat Spray"));
        assertAllowed(item(ItemIDs.absorbBulb, "Absorb Bulb"));
        assertAllowed(item(9001, "Adrenal Orb"));
        assertAllowed(item(ItemIDs.flameOrb, "Flame Orb"));
        assertAllowed(item(ItemIDs.toxicOrb, "Toxic Orb"));
        assertAllowed(item(ItemIDs.redCard, "Red Card"));
        assertAllowed(item(ItemIDs.hardStone, "Hard Stone"));
        assertAllowed(item(ItemIDs.everstone, "Everstone"));
        assertAllowed(item(9000, "Blk Augurite"));
    }

    @Test
    public void usefulBerriesAndSpecificHeldItemsOverrideLegacyBadFlags() {
        assertAllowedWhenBanBad(item(ItemIDs.oranBerry, "Oran Berry"));
        assertAllowedWhenBanBad(item(ItemIDs.lumBerry, "Lum Berry"));
        assertAllowedWhenBanBad(item(ItemIDs.lightBall, "Light Ball"));
        assertAllowedWhenBanBad(item(ItemIDs.thickClub, "Thick Club"));
    }

    @Test
    public void pokeBallsAreRecognizedAsAllowedRewardItems() {
        assertTrue(CfruDpeItemPoolPolicy.isPokeBallItem(item(ItemIDs.pokeBall, "Poke Ball")));
        assertTrue(CfruDpeItemPoolPolicy.isPokeBallItem(item(ItemIDs.masterBall, "Master Ball")));
        assertTrue(CfruDpeItemPoolPolicy.isPokeBallItem(item(9000, "Premier Ball")));
        assertFalse(CfruDpeItemPoolPolicy.isPokeBallItem(item(ItemIDs.potion, "Potion")));
    }

    @Test
    public void unknownItemsAreNotSilentlyAddedToNewBanOrAllowCategories() {
        Item unknown = item(9999, "Future Custom Item");

        assertFalse(CfruDpeItemPoolPolicy.isBannedFromNormalItemPools(unknown));
        assertFalse(CfruDpeItemPoolPolicy.isBadWhenBanBadItems(unknown));
    }

    private static void assertBadOnly(Item item) {
        assertFalse(CfruDpeItemPoolPolicy.isBannedFromNormalItemPools(item), item + " should stay allowed");
        assertTrue(CfruDpeItemPoolPolicy.isBadWhenBanBadItems(item), item + " should be Ban-Bad filtered");
    }

    private static void assertBanned(Item item) {
        assertTrue(CfruDpeItemPoolPolicy.isBannedFromNormalItemPools(item), item + " should be banned");
        assertFalse(CfruDpeItemPoolPolicy.isBadWhenBanBadItems(item), item + " should not need Ban-Bad filtering");
    }

    private static void assertAllowed(Item item) {
        assertFalse(CfruDpeItemPoolPolicy.isBannedFromNormalItemPools(item), item + " should stay allowed");
        assertFalse(CfruDpeItemPoolPolicy.isBadWhenBanBadItems(item), item + " should not be Ban-Bad filtered");
    }

    private static void assertAllowedWhenBanBad(Item item) {
        assertAllowed(item);
        assertTrue(CfruDpeItemPoolPolicy.isAllowedWhenBanBadItems(item), item + " should override legacy bad flags");
    }

    private static Item item(int id, String name) {
        return new Item(id, name);
    }

    private static Item cfruDpeItem(int sourceId, String name) {
        return item(CfruDpeItemCategories.standardIdForSourceId(sourceId), name);
    }
}
