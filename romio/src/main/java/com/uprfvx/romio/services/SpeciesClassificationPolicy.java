package com.uprfvx.romio.services;

import com.uprfvx.romio.gamedata.Species;

import java.util.Set;

/** Immutable Legendary/Mythical classification in the active handler's identity space. */
public final class SpeciesClassificationPolicy {
    private final Set<Integer> legendaryIdentities;

    private SpeciesClassificationPolicy(Set<Integer> legendaryIdentities) {
        this.legendaryIdentities = Set.copyOf(legendaryIdentities);
    }

    /** Preserve generic IDs, base ownership and existing legacy semantics exactly. */
    public static SpeciesClassificationPolicy legacy() { return LEGACY; }

    public static SpeciesClassificationPolicy cfruDpe() { return CFRU_DPE; }

    public boolean isLegendary(Species species) {
        if (this == LEGACY) return species.isLegendary();
        // DPE loads many alternate rows as independent base identities. Explicit rows
        // cover these, while modeled forms inherit the existing base-owner contract.
        return legendaryIdentities.contains(species.getSpeciesSetIdentityNumber())
                || legendaryIdentities.contains(species.getBaseForme().getSpeciesSetIdentityNumber());
    }

    private static final SpeciesClassificationPolicy LEGACY = new SpeciesClassificationPolicy(Set.of());

    /**
     * DPE d887185de1f6ae6a78e85c4311bbadde17041d00 include/species.h:
     * 94 semantic owners, 187 encoded base/form rows (including unsafe forms).
     * Semantics: the 68 existing UPR Legendary/Mythical owners plus 26 later
     * owners from the pilot's pinned Showdown reference. Ultra Beasts/Paradox
     * are separate categories. This policy classifies; #664 still controls eligibility.
     * See docs/src/_notes/legendary_classification_cfru_dpe.md and the frozen
     * test inventory for exact owner/ID/source bindings.
     */
    private static final SpeciesClassificationPolicy CFRU_DPE = new SpeciesClassificationPolicy(Set.of(
            0x090, 0x4C5, // ARTICUNO
            0x091, 0x4C6, // ZAPDOS
            0x092, 0x4C7, // MOLTRES
            0x096, 0x372, 0x373, // MEWTWO
            0x097, // MEW
            0x0F3, // RAIKOU
            0x0F4, // ENTEI
            0x0F5, // SUICUNE
            0x0F9, // LUGIA
            0x0FA, // HO_OH
            0x0FB, // CELEBI
            0x191, // REGIROCK
            0x192, // REGICE
            0x193, // REGISTEEL
            0x194, 0x38E, // KYOGRE
            0x195, 0x38D, // GROUDON
            0x196, 0x38F, // RAYQUAZA
            0x197, 0x38B, // LATIAS
            0x198, 0x38C, // LATIOS
            0x199, // JIRACHI
            0x19A, 0x410, 0x411, 0x412, // DEOXYS
            0x215, // UXIE
            0x216, // MESPRIT
            0x217, // AZELF
            0x218, 0x397, // DIALGA
            0x219, 0x398, // PALKIA
            0x21A, // HEATRAN
            0x21B, // REGIGIGAS
            0x21C, 0x2CE, // GIRATINA
            0x21D, // CRESSELIA
            0x21E, // PHIONE
            0x21F, // MANAPHY
            0x220, // DARKRAI
            0x221, 0x2CF, // SHAYMIN
            // ARCEUS and its 17 type forms
            0x222, 0x2D0, 0x2D1, 0x2D2, 0x2D3, 0x2D4, 0x2D5, 0x2D6, 0x2D7,
            0x2D8, 0x2D9, 0x2DA, 0x2DB, 0x2DC, 0x2DD, 0x2DE, 0x2DF, 0x342,
            0x223, // VICTINI
            0x2B3, // COBALION
            0x2B4, // TERRAKION
            0x2B5, // VIRIZION
            0x2B6, 0x2F2, // TORNADUS
            0x2B7, 0x2F3, // THUNDURUS
            0x2B8, // RESHIRAM
            0x2B9, // ZEKROM
            0x2BA, 0x2F4, // LANDORUS
            0x2BB, 0x2F0, 0x2F1, // KYUREM
            0x2BC, 0x2F5, // KELDEO
            0x2BD, 0x2EA, // MELOETTA
            0x2BE, 0x2EB, 0x2EC, 0x2ED, 0x2EE, // GENESECT
            0x338, 0x44D, // XERNEAS
            0x339, // YVELTAL
            0x33A, 0x343, 0x344, 0x345, 0x346, // ZYGARDE
            0x33B, 0x396, // DIANCIE
            0x33C, 0x33D, // HOOPA
            0x33E, // VOLCANION
            0x3DD, // TYPE_NULL
            // SILVALLY and its 17 type forms
            0x3DE, 0x418, 0x419, 0x41A, 0x41B, 0x41C, 0x41D, 0x41E, 0x41F,
            0x420, 0x421, 0x422, 0x423, 0x424, 0x425, 0x426, 0x427, 0x428,
            0x3EA, // TAPU_KOKO
            0x3EB, // TAPU_LELE
            0x3EC, // TAPU_BULU
            0x3ED, // TAPU_FINI
            0x3EE, // COSMOG
            0x3EF, // COSMOEM
            0x3F0, // SOLGALEO
            0x3F1, // LUNALA
            0x3F9, 0x437, 0x438, 0x439, // NECROZMA
            0x3FA, 0x431, // MAGEARNA
            0x3FB, // MARSHADOW
            0x436, // ZERAORA
            0x43B, // MELTAN
            0x43C, 0x4F9, // MELMETAL
            0x49C, 0x4B5, // ZACIAN
            0x49D, 0x4B6, // ZAMAZENTA
            0x49E, 0x4B7, // ETERNATUS
            0x49F, // KUBFU
            0x4A0, 0x50C, 0x4B8, 0x50D, // URSHIFU_SINGLE
            0x4A1, 0x4B9, // ZARUDE
            0x4A2, // REGIELEKI
            0x4A3, // REGIDRAGO
            0x4A4, // GLASTRIER
            0x4A5, // SPECTRIER
            0x4A6, 0x4BA, 0x4BB, // CALYREX
            0x4EA, 0x4EB, // ENAMORUS
            0x577, // WO_CHIEN
            0x578, // CHIEN_PAO
            0x579, // TING_LU
            0x57A, // CHI_YU
            0x57D, // KORAIDON
            0x57E, // MIRAIDON
            0x58B, // OKIDOGI
            0x58C, // MUNKIDORI
            0x58D, // FEZANDIPITI
            0x58E, 0x58F, 0x590, 0x591, 0x592, 0x593, 0x594, 0x595, // OGERPON
            0x59C, 0x59D, 0x59E, // TERAPAGOS
            0x59F // PECHARUNT
    ));
}
