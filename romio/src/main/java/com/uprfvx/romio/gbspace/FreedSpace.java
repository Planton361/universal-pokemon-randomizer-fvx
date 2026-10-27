package com.uprfvx.romio.gbspace;

/*----------------------------------------------------------------------------*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2022.                                 --*/
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Represents/handles all the manually freed bytes in a ROM. If bytes are manually freed (using free())
 * we know they should be available for writing at a later time, at which point we can findAndUnfree() them.
 **/
public class FreedSpace {

    private static final String ALREADY_FREED_EXCEPTION_MESSAGE =  "Can't free a space that is already freed. " +
            "This is a safety measure to prevent bad usage of free()." +
            "\n%s overlaps with existing freed chunk %s";

    protected static class FreedChunk {

        public int start, end;

        public FreedChunk(int offset, int end) {
            this.start = offset;
            this.end = end;
        }

        public int getLength() {
            return end - start + 1;
        }

        @Override
        public String toString() {
            return String.format("%x-%x", start, end);
        }

    }

    protected final LinkedList<FreedChunk> freedChunks = new LinkedList<>();

    /** Half-open physical interval. Registration is monotonic for this allocator. */
    public record ProtectedRange(String type, int start, int end) {
        public ProtectedRange {
            if (type == null || !type.matches("[A-Z][A-Z0-9_]{0,63}") || start < 0 || end <= start)
                throw new IllegalArgumentException("Invalid protected range.");
        }
    }

    private final List<ProtectedRange> protectedRanges = new ArrayList<>();
    // Reservations made by this allocator are conservatively live until freed
    // or a transaction snapshot is restored. Never claim they are free owners.
    private final List<FreedChunk> reservations = new ArrayList<>();

    private static boolean overlaps(int start, int end, int otherStart, int otherEnd) {
        return start < otherEnd && otherStart < end;
    }

    /** Atomically exclude a set of separately typed, disjoint physical owners. */
    public final void protectRanges(List<ProtectedRange> ranges) {
        if (ranges == null || ranges.isEmpty()) throw new IllegalArgumentException("Missing protected ranges.");
        List<ProtectedRange> combined = new ArrayList<>(protectedRanges);
        for (ProtectedRange r : ranges) {
            if (r == null) throw new IllegalArgumentException("Null protected range.");
            for (FreedChunk allocation : reservations) {
                if ((long) r.start() <= allocation.end && (long) allocation.start < r.end())
                    throw new IllegalStateException("Protected range overlaps an active reservation.");
            }
            combined.add(r);
        }
        combined.sort(Comparator.comparingInt(ProtectedRange::start));
        for (int i = 1; i < combined.size(); i++) {
            if (combined.get(i - 1).end() > combined.get(i).start())
                throw new IllegalArgumentException("Overlapping protected ranges.");
        }
        LinkedList<FreedChunk> carved = withoutProtected(freedChunks, combined);
        protectedRanges.clear(); protectedRanges.addAll(combined);
        freedChunks.clear(); freedChunks.addAll(carved);
    }

    private static LinkedList<FreedChunk> withoutProtected(Iterable<FreedChunk> chunks, List<ProtectedRange> excluded) {
        LinkedList<FreedChunk> result = new LinkedList<>();
        for (FreedChunk chunk : chunks) {
            long cursor = chunk.start, end = (long) chunk.end + 1;
            for (ProtectedRange range : excluded) {
                if (range.end() <= cursor) continue;
                if (range.start() >= end) break;
                if (cursor < range.start()) result.add(new FreedChunk((int) cursor, range.start() - 1));
                cursor = Math.max(cursor, (long) range.end());
                if (cursor >= end) break;
            }
            if (cursor < end) result.add(new FreedChunk((int) cursor, (int) (end - 1)));
        }
        return result;
    }

    private void releaseReservations(int start, int end) {
        List<FreedChunk> remaining = new ArrayList<>();
        for (FreedChunk r : reservations) {
            long rEnd = (long) r.end + 1;
            if ((long) start >= rEnd || end <= r.start) remaining.add(r);
            else {
                if (r.start < start) remaining.add(new FreedChunk(r.start, start - 1));
                if (end < rEnd) remaining.add(new FreedChunk(end, r.end));
            }
        }
        reservations.clear(); reservations.addAll(remaining);
    }

    /** A deep, immutable ledger belonging exclusively to the allocator that captured it. */
    public static final class Snapshot {
        private final FreedSpace owner;
        private final int[][] chunks;
        private final int[][] reservations;

        private Snapshot(FreedSpace owner) {
            this.owner = owner;
            chunks = new int[owner.freedChunks.size()][2];
            int i = 0;
            for (FreedChunk chunk : owner.freedChunks) {
                chunks[i++] = new int[]{chunk.start, chunk.end};
            }
            reservations = new int[owner.reservations.size()][2]; i = 0;
            for (FreedChunk r : owner.reservations) reservations[i++] = new int[]{r.start, r.end};
        }
    }

    public final Snapshot snapshot() {
        return new Snapshot(this);
    }

    /** Restores all chunks, including reserved banks, without changing bank configuration. */
    public final void restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.owner != this) {
            throw new IllegalArgumentException("Snapshot belongs to a different allocator.");
        }
        LinkedList<FreedChunk> restored = new LinkedList<>();
        for (int[] chunk : snapshot.chunks) {
            restored.add(new FreedChunk(chunk[0], chunk[1]));
        }
        LinkedList<FreedChunk> safe = withoutProtected(restored, protectedRanges);
        List<FreedChunk> restoredReservations = new ArrayList<>();
        for (int[] r : snapshot.reservations) restoredReservations.add(new FreedChunk(r[0], r[1]));
        LinkedList<FreedChunk> safeReservations = withoutProtected(restoredReservations, protectedRanges);
        freedChunks.clear(); freedChunks.addAll(safe);
        reservations.clear(); reservations.addAll(safeReservations);
    }

    /** Validate a release before callers mutate any associated bytes. */
    public final void checkFreeRange(int start, int length) {
        if (length < 1) throw new IllegalArgumentException("length must be at least 1");
        long end = (long) start + length;
        if (!protectedRanges.isEmpty() && (start < 0 || end > Integer.MAX_VALUE))
            throw new IllegalArgumentException("Invalid free range.");
        for (ProtectedRange r : protectedRanges)
            if ((long) start < r.end() && r.start() < end)
                throw new IllegalStateException("Cannot free protected range.");
    }

    public void free(int start, int length) {
        // System.out.printf("freeing %d bytes starting from 0x%x.%n", length, start);
        if (length < 1) {
            throw new IllegalArgumentException("length must be at least 1");
        }

        checkFreeRange(start, length);
        long exclusiveEnd = (long) start + length;
        int end = start + length - 1;
        FreedChunk toFree = new FreedChunk(start, end);

        boolean freedChunkAdded = false;
        ListIterator<FreedChunk> iterator = freedChunks.listIterator();
        while (iterator.hasNext() && !freedChunkAdded) {
            FreedChunk neighbor = iterator.next();

            if (toFree.start < neighbor.start) {
                addChunkBeforeOrMergeWithRightNeighbor(toFree, neighbor, iterator);
                freedChunkAdded = true;

            } else if (neighbor.end == toFree.start - 1) {
                mergeChunkWithLeftOrBothNeighbors(toFree, neighbor, iterator);
                freedChunkAdded = true;

            } else if (neighbor.end >= toFree.start) {
                throw new RuntimeException(String.format(ALREADY_FREED_EXCEPTION_MESSAGE, toFree, neighbor));
            }
        }

        if (!freedChunkAdded) {
            freedChunks.add(toFree);
        }
        if (exclusiveEnd <= Integer.MAX_VALUE && exclusiveEnd >= Integer.MIN_VALUE)
            releaseReservations(start, (int) exclusiveEnd);
        // System.out.println("after:\t" + this);
    }

    protected final void addChunkBeforeOrMergeWithRightNeighbor(FreedChunk toFree, FreedChunk rightNeighbor,
                                                                ListIterator<FreedChunk> iterator) {
        if (toFree.end >= rightNeighbor.start) {
            throw new RuntimeException(String.format(ALREADY_FREED_EXCEPTION_MESSAGE, toFree, rightNeighbor));
        }

        if (toFree.end == rightNeighbor.start - 1) {
            rightNeighbor.start = toFree.start;
        } else {
            iterator.previous();
            iterator.add(toFree);
        }
    }

    protected final void mergeChunkWithLeftOrBothNeighbors(FreedChunk toFree, FreedChunk leftNeighbor,
                                                           ListIterator<FreedChunk> iterator) {
        if (iterator.hasNext()) {
            FreedChunk rightNeighbor = iterator.next();

            if (toFree.end >= rightNeighbor.start) {
                throw new RuntimeException(String.format(ALREADY_FREED_EXCEPTION_MESSAGE, toFree, rightNeighbor));
            } else if (toFree.end == rightNeighbor.start - 1) {
                leftNeighbor.end = rightNeighbor.end;
                iterator.remove();
            } else {
                leftNeighbor.end = toFree.end;
            }
            iterator.previous();

        } else {
            leftNeighbor.end = toFree.end;
        }
    }

    public int findAndUnfree(int length) {
        if (!protectedRanges.isEmpty() && length < 1) throw new IllegalArgumentException("Invalid allocation length.");
        // System.out.println("looking for " + length + " bytes");
        FreedChunk found = find(length);
        if (found == null) {
            return -1;
        }
        int offset = found.start;
        unfree(found, length);
        // System.out.println("after:\t" + this);
        return offset;
    }

    protected final FreedChunk find(int length) {
        for (FreedChunk fc : freedChunks) {
            if (fc.getLength() >= length) {
                return fc;
            }
        }
        return null;
    }

    protected final void unfree(FreedChunk toUnfree, int length) {
        // System.out.println("unfreeing " + length + " bytes starting from 0x" + Integer.toHexString(toUnfree.start));
        if (length > 0) reservations.add(new FreedChunk(toUnfree.start, toUnfree.start + length - 1));
        toUnfree.start += length;
        if (toUnfree.start > toUnfree.end) {
            freedChunks.remove(toUnfree);
        }
    }

    public int getLengthSum() {
        int sum = 0;
        for (FreedChunk fs : freedChunks) {
            sum += fs.getLength();
        }
        return sum;
    }

    @Override
    public String toString() {
		return getLengthSum() + " bytes, " + freedChunks.size() + " chunks, " + freedChunks;
	}

}
