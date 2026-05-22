package com.uprfvx.random.randomizers;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

final class ItemFamilyTicketSampler {

    private static final Set<String> BERRY_NAMES = Set.of(
            "cheriberry", "chestoberry", "pechaberry", "rawstberry", "aspearberry",
            "leppaberry", "oranberry", "persimberry", "lumberry", "sitrusberry",
            "figyberry", "wikiberry", "magoberry", "aguavberry", "iapapaberry",
            "razzberry", "blukberry", "nanabberry", "wepearberry", "pinapberry",
            "pomegberry", "kelpsyberry", "qualotberry", "hondewberry", "grepaberry",
            "tamatoberry", "cornnberry", "magostberry", "rabutaberry", "nomelberry",
            "spelonberry", "pamtreberry", "watmelberry", "durinberry", "belueberry",
            "occaberry", "passhoberry", "wacanberry", "rindoberry", "yacheberry",
            "chopleberry", "kebiaberry", "shucaberry", "cobaberry", "payapaberry",
            "tangaberry", "chartiberry", "kasibberry", "habanberry", "colburberry",
            "babiriberry", "chilanberry", "liechiberry", "ganlonberry", "salacberry",
            "petayaberry", "apicotberry", "lansatberry", "starfberry", "enigmaberry",
            "micleberry", "custapberry", "jabocaberry", "rowapberry", "roseliberry",
            "keeberry", "marangaberry"
    );
    private static final Set<String> GEM_NAMES = Set.of(
            "firegem", "watergem", "electricgem", "grassgem", "icegem",
            "fightinggem", "poisongem", "groundgem", "flyinggem", "psychicgem",
            "buggem", "rockgem", "ghostgem", "dragongem", "darkgem", "steelgem",
            "normalgem", "fairygem"
    );

    private ItemFamilyTicketSampler() {
    }

    static TicketPool ticketPool(Collection<Item> items) {
        List<Ticket> regularTickets = new ArrayList<>();
        List<Item> berries = new ArrayList<>();
        List<Item> gems = new ArrayList<>();

        for (Item item : items) {
            if (isBerry(item)) {
                berries.add(item);
            } else if (isGem(item)) {
                gems.add(item);
            } else {
                regularTickets.add(new SingleItemTicket(item));
            }
        }

        addFamilyTicket(regularTickets, berries);
        addFamilyTicket(regularTickets, gems);
        return new TicketPool(regularTickets);
    }

    private static void addFamilyTicket(List<Ticket> tickets, List<Item> familyItems) {
        if (familyItems.isEmpty()) {
            return;
        }
        if (familyItems.size() == 1) {
            tickets.add(new SingleItemTicket(familyItems.get(0)));
        } else {
            tickets.add(new FamilyTicket(familyItems));
        }
    }

    private static boolean isBerry(Item item) {
        if (item == null) {
            return false;
        }
        int id = item.getId();
        return (id >= ItemIDs.cheriBerry && id <= ItemIDs.rowapBerry)
                || (id >= ItemIDs.roseliBerry && id <= ItemIDs.marangaBerry)
                || BERRY_NAMES.contains(normalizedName(item));
    }

    private static boolean isGem(Item item) {
        if (item == null) {
            return false;
        }
        int id = item.getId();
        return (id >= ItemIDs.fireGem && id <= ItemIDs.normalGem)
                || id == ItemIDs.fairyGem
                || GEM_NAMES.contains(normalizedName(item));
    }

    private static String normalizedName(Item item) {
        String name = item.getName();
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    interface Ticket {
        Item resolve(Random random);
    }

    static final class TicketPool {
        private final List<Ticket> tickets;

        private TicketPool(List<Ticket> tickets) {
            this.tickets = Collections.unmodifiableList(new ArrayList<>(tickets));
        }

        boolean isEmpty() {
            return tickets.isEmpty();
        }

        Item pick(Random random) {
            return tickets.get(random.nextInt(tickets.size())).resolve(random);
        }

        void refillShuffled(Stack<Ticket> stack, Random random) {
            stack.addAll(tickets);
            Collections.shuffle(stack, random);
        }
    }

    private record SingleItemTicket(Item item) implements Ticket {
        @Override
        public Item resolve(Random random) {
            return item;
        }
    }

    private static final class FamilyTicket implements Ticket {
        private final List<Item> familyItems;

        private FamilyTicket(List<Item> familyItems) {
            this.familyItems = Collections.unmodifiableList(new ArrayList<>(familyItems));
        }

        @Override
        public Item resolve(Random random) {
            return familyItems.get(random.nextInt(familyItems.size()));
        }
    }
}
