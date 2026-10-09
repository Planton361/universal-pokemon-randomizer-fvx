package com.uprfvx.random.randomizers;

/*----------------------------------------------------------------------------*/
/*--  Part of "Universal Pokemon Randomizer" by Dabomstew                   --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2012.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.cueh.CopyUpEvolutionsHelper;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.gamedata.cueh.BasicSpeciesAction;
import com.uprfvx.romio.gamedata.cueh.EvolvedSpeciesAction;
import com.uprfvx.romio.graphics.palettes.*;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * A {@link PaletteRandomizer} for Gen 3, Gen 4, and Gen 5 games (R/S/E/FR/LG,
 * D/P/Pt/HG/SS, B/W/B2/W2).
 * <p>
 * All three generations use similar 16-color palettes, that are implemented
 * using the {@link Palette} class. These palettes are populated/filled/modified
 * by {@link PalettePopulator}, using {@link PalettePartDescription}s as
 * instructions.
 * <p>
 * When Pokémon palettes are randomized, each Pokémon is assigned a
 * {@link TypeBaseColorList}, which uses its types to come up with appropriate
 * base colors.
 */
public class Gen3to5PaletteRandomizer extends PaletteRandomizer {

	/**
	 * An identifier for the related resource files. ROMs that share a
	 * paletteFilesID also share all resources. If they shouldn't, different ROMs
	 * must be assigned separate IDs.
	 */
	private final String paletteFilesID;

	private boolean typeSanity;
	private boolean shinyFromNormal;
	private Map<Species, TypeBaseColorList> typeBaseColorLists;

	public Gen3to5PaletteRandomizer(RomHandler romHandler, Settings settings, Random random) {
		super(romHandler, settings, random);
		this.paletteFilesID = romHandler.getPaletteFilesID();
	}

	@Override
	public void randomizePokemonPalettes() {

		// TODO: Figure out what to do with forms, with different palettes and with the same.
		// TODO: figure out genders in gen V, if anything needs to be done at all

		this.typeSanity = settings.isPokemonPalettesFollowTypes();
		this.shinyFromNormal = settings.isPokemonPalettesShinyFromNormal();
		boolean evolutionSanity = settings.isPokemonPalettesFollowEvolutions();

        if (romHandler instanceof Gen3RomHandler gen3 && gen3.usesCfruDpeRandomPoolPolicy()
                && shinyFromNormal) {
            changesMade = false;
            if (settings.getPokemonPalettesMod() != Settings.PokemonPalettesMod.RANDOM) {
                return;
            }
            if (typeSanity) {
                throw new RandomizationException("CFRU/DPE Follow Types + Shiny From Normal requires separate approval.");
            }
            randomizeCfruDpePalettePairs(gen3, evolutionSanity);
            return;
        }

		this.typeBaseColorLists = new HashMap<>();

		if (paletteFilesID == null) {
			// TODO: this is the kind of exception which the user could basically ignore, the ROM is still
			//  fully functional. Is there a better way of logging that?
			//  e.g. - You click "randomize"
			//  - Something throws an exception here in the PaletteRandomizer
			//  - The Randomizer catches it and prints it to a log, doesn't write the palettes.
			//  - The rest of the randomization continues
			//  - It finishes and the end-user gets the pop-up message
			//  "The randomization finished, but with some errors. See..."
			throw new RandomizationException("Could not randomize palettes, unrecognized romtype.");
		}

		copyUpEvolutionsHelper.apply(evolutionSanity, true, new BasicSpeciesPaletteAction(),
				new EvolvedSpeciesPaletteAction());
		List<PaletteDescription> paletteDescriptions = getPaletteDescriptions("pokePalettes");
		populatePokemonPalettes(paletteDescriptions);
		changesMade = !typeBaseColorLists.isEmpty();

	}

    private record PalettePairPlan(Palette normal, Palette shiny) { }

    private void randomizeCfruDpePalettePairs(Gen3RomHandler handler, boolean followEvolutions) {
        if (!"FRLG".equals(paletteFilesID)) {
            throw new RandomizationException("CFRU/DPE Shiny From Normal requires FRLG palette descriptions.");
        }
        List<Species> species = new ArrayList<>(romHandler.getSpeciesSetInclFormes());
        species.sort(Comparator.comparingInt(Species::getSpeciesSetIdentityNumber));
        Map<String, Integer> skipped = new TreeMap<>();
        Map<Species, PalettePartDescription[]> parts = new IdentityHashMap<>();
        List<PaletteDescription> descriptions = getPaletteDescriptions("pokePalettes");
        for (Species pk : species) {
            var eligibility = handler.getCfruDpePalettePairEligibility(pk);
            String reason = eligibility.eligible() ? null : eligibility.reason();
            if (reason == null && pk.getPrimaryType(false) == null) {
                reason = "invalid primary type";
            }
            if (reason == null) {
                PalettePartDescription[] bounded = boundedCfruDpeParts(pk, descriptions);
                if (bounded == null) {
                    reason = "missing/invalid recolor description";
                } else {
                    parts.put(pk, bounded);
                }
            }
            if (reason != null) {
                skipped.merge(reason, 1, Integer::sum);
            }
        }
        if (followEvolutions) {
            validateCfruDpePaletteGraph(species);
            // Preserve an entire connected chain if any member cannot prove the pair contract.
            // No child or sibling inherits colors from an unloaded/skipped parent.
            boolean removed;
            do {
                removed = false;
                for (Species pk : species) {
                    if (parts.containsKey(pk) && (pk.getEvolutionsTo().stream().anyMatch(e -> !parts.containsKey(e.getFrom()))
                            || pk.getEvolutionsFrom().stream().anyMatch(e -> !parts.containsKey(e.getTo())))) {
                        parts.remove(pk);
                        skipped.merge("ineligible evolution component", 1, Integer::sum);
                        removed = true;
                    }
                }
            } while (removed);
        }
        SpeciesSet eligible = new SpeciesSet(species.stream().filter(parts::containsKey).toList());
        Map<Species, TypeBaseColorList> colors = new IdentityHashMap<>();
        new CopyUpEvolutionsHelper<Species>(eligible).apply(followEvolutions, true,
                pk -> colors.put(pk, new TypeBaseColorList(pk, false, random)),
                (from, to, last) -> colors.put(to, new TypeBaseColorList(to, colors.get(from), false, random)));
        Map<Species, PalettePairPlan> plan = new IdentityHashMap<>();
        PalettePopulator populator = new PalettePopulator(random);
        boolean anyChanged = false;
        for (Species pk : species) {
            if (!parts.containsKey(pk)) {
                continue;
            }
            // Eligibility proved that the live normal still equals the original load snapshot.
            Palette shiny = new Palette(pk.getNormalPalette());
            Palette normal = new Palette(pk.getNormalPalette());
            populatePalette(normal, populator, colors.get(pk), parts.get(pk));
            anyChanged |= !Arrays.equals(normal.toBytes(), pk.getNormalPalette().toBytes())
                    || !Arrays.equals(shiny.toBytes(), pk.getShinyPalette().toBytes());
            plan.put(pk, new PalettePairPlan(normal, shiny));
        }
        // No field is published before every scratch pair has completed, including late failures.
        for (Species pk : species) {
            PalettePairPlan pair = plan.get(pk);
            if (pair != null) {
                pk.setNormalPalette(pair.normal());
                pk.setShinyPalette(pair.shiny());
            }
        }
        changesMade = anyChanged;
        System.err.println("CFRU/DPE GFX-004: plannedPairs=" + plan.size()
                + " changed=" + changesMade + " skipped=" + skipped);
    }

    private PalettePartDescription[] boundedCfruDpeParts(Species pk, List<PaletteDescription> descriptions) {
        int index = pk.getNumber() - 1;
        if (descriptions == null || index < 0 || index >= descriptions.size()
                || descriptions.get(index) == null || descriptions.get(index).getBody().isBlank()) {
            return null;
        }
        PalettePartDescription[] parts;
        try {
            parts = getPalettePartDescriptions(pk, descriptions);
        } catch (NumberFormatException ex) {
            return null;
        }
        // The existing TypeBaseColorList basic loop supplies eight indexed colors, including
        // blank/average positions. Do not extend its algorithm to support a speculative ninth.
        if (parts.length > 8) {
            return null;
        }
        boolean recolors = false;
        for (PalettePartDescription part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (part.isAverageDescription()) {
                if (!validPaletteSlot(part.getAverageToSlot(), false)
                        || part.getAverageFromSlots().length == 0
                        || Arrays.stream(part.getAverageFromSlots()).anyMatch(i -> !validPaletteSlot(i, false))) {
                    return null;
                }
            } else {
                if (part.length() == 0 || Arrays.stream(part.getSlots()).anyMatch(i -> !validPaletteSlot(i, true))) {
                    return null;
                }
                recolors |= Arrays.stream(part.getSlots()).anyMatch(i -> i >= 0);
                if (part.hasSibling()) {
                    int shared = part.getSharedSlot();
                    if (!validPaletteSlot(shared, false)
                            || Arrays.stream(part.getSiblingSlots()).noneMatch(i -> i == shared)
                            || Arrays.stream(part.getSiblingSlots()).anyMatch(i -> !validPaletteSlot(i, true))) {
                        return null;
                    }
                }
            }
        }
        return recolors ? parts : null;
    }

    private boolean validPaletteSlot(int slot, boolean allowUnusedShade) {
        return slot >= (allowUnusedShade ? -1 : 0) && slot < 16;
    }

    private void validateCfruDpePaletteGraph(List<Species> species) {
        Set<Species> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.addAll(species);
        for (Species pk : species) {
            Species parent = null;
            for (Evolution edge : pk.getEvolutionsTo()) {
                if (edge == null || edge.getTo() != pk || !selected.contains(edge.getFrom())
                        || edge.getType() == null || !edge.getFrom().getEvolutionsFrom().contains(edge)) {
                    throw new RandomizationException("CFRU/DPE GFX-004: invalid incoming evolution.");
                }
                // Two methods from the same parent (e.g. Feebas) have the same color owner.
                if (parent != null && parent != edge.getFrom()) {
                    throw new RandomizationException("CFRU/DPE GFX-004: ambiguous evolution parents.");
                }
                parent = edge.getFrom();
            }
            for (Evolution edge : pk.getEvolutionsFrom()) {
                if (edge == null || edge.getFrom() != pk || !selected.contains(edge.getTo())
                        || edge.getType() == null || !edge.getTo().getEvolutionsTo().contains(edge)) {
                    throw new RandomizationException("CFRU/DPE GFX-004: invalid outgoing evolution.");
                }
            }
        }
        // Iterative bounded cycle detection, before the legacy helper follows any parent chain.
        Set<Species> complete = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Species pk : species) {
            Set<Species> path = Collections.newSetFromMap(new IdentityHashMap<>());
            Species cursor = pk;
            while (!complete.contains(cursor)) {
                if (!path.add(cursor)) {
                    throw new RandomizationException("CFRU/DPE GFX-004: cyclic evolution graph.");
                }
                if (cursor.getEvolutionsTo().isEmpty()) {
                    break;
                }
                cursor = cursor.getEvolutionsTo().get(0).getFrom();
            }
            complete.addAll(path);
        }
    }

	private void populatePokemonPalettes(List<PaletteDescription> paletteDescriptions) {

		PalettePopulator pp = new PalettePopulator(random);

		for (Entry<Species, TypeBaseColorList> entry : typeBaseColorLists.entrySet()) {

			Species pk = entry.getKey();
			Palette palette = pk.getNormalPalette();
			TypeBaseColorList typeBaseColorList = entry.getValue();
			PalettePartDescription[] palettePartDescriptions = getPalettePartDescriptions(pk, paletteDescriptions);

			populatePalette(palette, pp, typeBaseColorList, palettePartDescriptions);

		}
	}

	public void populatePalette(Palette palette, PalettePopulator pp, TypeBaseColorList typeBaseColorList,
			PalettePartDescription[] palettePartDescriptions) {

		for (int i = 0; i < palettePartDescriptions.length; i++) {

			if (palettePartDescriptions[i].isAverageDescription()) {
				pp.populateAverageColor(palette, palettePartDescriptions[i]);

			} else if (!palettePartDescriptions[i].isBlank()) {
				Color baseColor = typeBaseColorList.getBaseColor(i);
				LightDarkMode lightDarkMode = typeBaseColorList.getLightDarkMode(i);
				pp.populatePartFromBaseColor(palette, palettePartDescriptions[i], baseColor, lightDarkMode);
			}

		}
	}

	public PalettePartDescription[] getPalettePartDescriptions(Species pk,
                                                               List<PaletteDescription> paletteDescriptions) {
		int paletteIndex = pk.getNumber() - 1;
		boolean validIndex = paletteIndex >= 0 && paletteIndex < paletteDescriptions.size();
		return PalettePartDescription
				.allFrom(validIndex ? paletteDescriptions.get(paletteIndex) : PaletteDescription.BLANK);
	}

	/**
	 * Gets {@link PaletteDescription}s from a resource/file.
	 * 
	 * @param fileKey         The key to this particular kind of file, e.g.
	 *                        "pokePalettes".
	 */
	public List<PaletteDescription> getPaletteDescriptions(String fileKey) {
		List<PaletteDescription> paletteDescriptions = new ArrayList<>();

        InputStream is = getClass().getResourceAsStream(getResourceAddress(fileKey));
        if (is == null) {
            throw new RuntimeException(new RuntimeException("Could not find resource " + getResourceAddress(fileKey)));
        }
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		String line;
		try {
			while ((line = br.readLine()) != null) {
				paletteDescriptions.add(new PaletteDescription(line));
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not read palette description file " + getSourceFileAddress(fileKey) + ".");
		}

		return paletteDescriptions;
	}

	public void savePaletteDescriptionSource(String fileKey, List<PaletteDescription> paletteDescriptions) {
        // TODO: not removing this, because I believe it is used by PaletteDescriptionTool?
        //  It probably doesn't belong here either way, but would be silly to remove it
        //  before having a better alternative.
		String fileAdress = getSourceFileAddress(fileKey);

		try (PrintWriter writer = new PrintWriter(new FileWriter(fileAdress))) {

			for (int i = 0; i < paletteDescriptions.size(); i++) {
				writer.print(paletteDescriptions.get(i).toFileFormattedString());
				if (i != paletteDescriptions.size() - 1) {
					writer.print("\n");
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String getFileName(String fileKey) {
		return fileKey + paletteFilesID + ".txt";
	}

	private String getResourceAddress(String fileKey) {
		return "/com/uprfvx/romio/graphics/" + getFileName(fileKey);
	}

	private String getSourceFileAddress(String fileKey) {
		return "src/main/java/resources/com/uprfvx/romio/graphics/" + getFileName(fileKey);
	}

	private class BasicSpeciesPaletteAction implements BasicSpeciesAction<Species> {

		@Override
		public void applyTo(Species pk) {
			if (shinyFromNormal) {
				setShinyPaletteFromNormal(pk);
			}

			TypeBaseColorList typeBaseColorList = new TypeBaseColorList(pk, typeSanity, random);
			typeBaseColorLists.put(pk, typeBaseColorList);

		}

	}

	private class EvolvedSpeciesPaletteAction implements EvolvedSpeciesAction<Species> {

		@Override
		public void applyTo(Species evFrom, Species evTo, boolean toMonIsFinalEvo) {
			if (shinyFromNormal) {
				setShinyPaletteFromNormal(evTo);
			}
			TypeBaseColorList prevo = typeBaseColorLists.get(evFrom);
			TypeBaseColorList typeBaseColorList = new TypeBaseColorList(evTo, prevo, typeSanity, random);
			typeBaseColorLists.put(evTo, typeBaseColorList);

		}

	}

}
