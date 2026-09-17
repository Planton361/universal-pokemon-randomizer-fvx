#!/usr/bin/env python3
"""Execute the actual Pickup entry points with synthetic Java services, no Gradle/ROM."""
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]


def method(source, name):
    match = re.search(r"(?:public|private) [^\n]+ " + name + r"\([^)]*\)\s*\{", source)
    if not match:
        raise ValueError("Missing method: " + name)
    depth, end = 1, match.end()
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[match.start():end]


def main():
    source = (ROOT / "romio/src/main/java/com/uprfvx/romio/romhandlers/Gen3RomHandler.java").read_text()
    bodies = "\n".join(method(source, name) for name in
        ("getPickupItems", "setPickupItems", "rejectUnsupportedCfruDpePickup"))
    harness = r'''
import java.util.*;
public class PickupGuardHost {
    boolean useCfruDpeGen9SpeciesCount;
    Entry romEntry = new Entry();
    byte[] rom = new byte[128];
    int pickupItemsTableOffset;
    List<Item> items = new ArrayList<>();
    static class RomIOException extends RuntimeException { RomIOException(String s) { super(s); } }
    static class Item { int id; Item(int id) { this.id=id; } int getId(){return id;} }
    static class PickupItem {
        Item item; int[] probs = new int[10]; PickupItem(Item item){this.item=item;}
        int[] getProbabilities(){return probs;} Item getItem(){return item;}
    }
    static class Entry {
        int getIntValue(String key){return 16;}
        int getRomType(){return Gen3Constants.RomType_FRLG;}
    }
    static class Gen3Constants {
        static final int RomType_Em=1, RomType_Ruby=2, RomType_Sapp=3, RomType_FRLG=4;
        static int itemIDToStandard(int id){return id;}
        static int itemIDToInternal(int id){return id;}
    }
    static class IOFunctions {
        static int read2ByteInt(byte[] a, int p){return (a[p]&255)|((a[p+1]&255)<<8);}
        static void write2ByteInt(byte[] a,int p,int n){a[p]=(byte)n;a[p+1]=(byte)(n>>>8);}
    }
    void resolvePickupItemsTableOffset(int count,int stride){pickupItemsTableOffset=16;}
    /* METHODS */
    static void require(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        PickupGuardHost h = new PickupGuardHost();
        for(int i=0;i<32;i++) h.items.add(new Item(i));
        for(int i=0;i<16;i++) IOFunctions.write2ByteInt(h.rom,16+i*4,i+1);
        byte[] original = h.rom.clone();
        h.useCfruDpeGen9SpeciesCount=true;
        // Null metadata catches any attempted access before the guard.
        h.romEntry=null;
        for(int op=0;op<3;op++){
            try {
                if(op==0) h.getPickupItems();
                else h.setPickupItems(op==1?List.of():List.of(new PickupItem(new Item(31))));
                throw new AssertionError("unsupported operation accepted");
            } catch(RomIOException e){require(e.getMessage().contains("Pickup Items to Unchanged"));}
            require(Arrays.equals(original,h.rom));
            require(h.pickupItemsTableOffset==0);
        }
        h.useCfruDpeGen9SpeciesCount=false; h.romEntry=new Entry();
        List<PickupItem> list=h.getPickupItems(); require(list.size()==16);
        for(int i=0;i<16;i++)require(list.get(i).item.id==i+1);
        for(int level=0;level<10;level++){
            int sum=0; for(PickupItem p:list)sum+=p.probs[level]; require(sum==100);
        }
        list.get(0).item=new Item(31); h.setPickupItems(list);
        original[16]=31; require(Arrays.equals(original,h.rom));
        System.out.println("PASS: actual Pickup get/set entry points reject CFRU before table access/write; vanilla 16-entry read/write and 100% probability controls preserved");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="pickup-host-") as tmp:
        test = Path(tmp) / "PickupGuardHost.java"
        test.write_text(harness.replace("/* METHODS */", bodies))
        subprocess.run(["javac", "-d", tmp, str(test)], check=True)
        subprocess.run(["java", "-cp", tmp, "PickupGuardHost"], check=True)


if __name__ == "__main__":
    main()
