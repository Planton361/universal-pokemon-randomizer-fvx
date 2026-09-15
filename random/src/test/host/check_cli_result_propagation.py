#!/usr/bin/env python3
"""ROM-free execution of production result/completion/exit code with fake services.

Extracts the real randomization catch/result, Pickup setting dispatch, CLI call
sites and exit expression. ROM opening, argument parsing, other randomizers and
storage are deliberately synthetic; the companion ROM-layer harness exercises
the actual Pickup table entry points. This is not a substitute for module tests.
"""
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]


def block(source, pattern):
    match = re.search(pattern + r"\s*\{", source)
    if match is None:
        raise AssertionError("Production boundary changed: " + pattern)
    depth, end = 1, match.end()
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[match.start():end]


def main():
    base = ROOT / "random/src/main/java/com/uprfvx/random"
    game = (base / "GameRandomizer.java").read_text()
    cli = (base / "cli/CliRandomizer.java").read_text()
    launcher = (base / "RandomizerLauncher.java").read_text()
    rom = (ROOT / "romio/src/main/java/com/uprfvx/romio/romhandlers/Gen3RomHandler.java").read_text()
    results = block(game, r"public static class Results")
    randomize = block(game, r"public Results randomize\(final String filename, final PrintStream log, long seed\)")
    pickup = block(game, r"private void maybeRandomizePickupItems\(\)")
    guard = block(rom, r"private void rejectUnsupportedCfruDpePickup\(\)")
    completion = block(cli, r"static boolean completeRandomization\([^)]*\)")
    utilities = "\n".join(block(cli, r"private static \w+ " + name + r"\([^)]*\)")
                          for name in ("usageError", "printError", "printWarning", "printUsage"))
    perform = re.search(r"GameRandomizer randomizer = new GameRandomizer.*?return completeRandomization\([^;]+;", cli, re.S)
    assert perform, "CLI must consume Results through the tested completion boundary"
    invoke = block(cli, r"public static int invoke\(String\[\] args\)")
    invoke_tail = invoke[invoke.index("boolean processResult ="):]
    exit_call = re.search(r"System\.exit\(CliRandomizer\.invoke\(commandArgs\)\);", launcher)
    assert exit_call, "Launcher failure convention changed"
    harness = r'''
import java.io.*;
public class CliResultHost {
    static String scenario;
    static class RomIOException extends RuntimeException { RomIOException(String s){super(s);} }
    static class Settings {
        enum PickupItemsMod { RANDOM, UNCHANGED }
        PickupItemsMod getPickupItemsMod(){return scenario.equals("unchanged") ? PickupItemsMod.UNCHANGED : PickupItemsMod.RANDOM;}
    }
    static class Handler {
        boolean useCfruDpeGen9SpeciesCount = scenario.equals("blocked") || scenario.equals("unchanged");
        int reads, writes, saves;
        /* GUARD */
        void pickup(){rejectUnsupportedCfruDpePickup(); reads++; writes++;}
        boolean shouldWriteCheckValue(){return false;}
        void writeCheckValue(int n){throw new AssertionError("unexpected check write");}
        boolean saveRom(String f,long s,boolean d){saves++; return !scenario.equals("save-false");}
    }
    static class RandomSource {void seed(long n){}}
    static class Logger {void logResults(PrintStream p,long n){}}
    static class CheckValueCalculator {CheckValueCalculator(Handler h,Settings s){} int calculate(){return 0;}}
    static class Items {Handler h; Items(Handler h){this.h=h;} void randomizePickupItems(){h.pickup();}}
    static class GameRandomizer {
        /* RESULTS */
        Settings settings; Handler romHandler; boolean saveAsDirectory; Items itemRandomizer;
        RandomSource randomSource=new RandomSource(); Logger logger=new Logger();
        GameRandomizer(Settings s,Object c,Handler h,Object b,boolean d){settings=s;romHandler=h;saveAsDirectory=d;itemRandomizer=new Items(h);}
        void setupSpeciesRestrictions(){} void applyUpdaters(){} void maybeSetCustomPlayerGraphics(){}
        void applyRandomizers(){if(scenario.equals("generic-error"))throw new IllegalArgumentException("Synthetic failure");maybeRandomizePickupItems();}
        /* PICKUP */
        /* RANDOMIZE */
    }
    static class CliRandomizer {
        /* COMPLETION */
        /* UTILITIES */
        static boolean performDirectRandomization(String src,String filename,Settings settings,long seed,
                Object cpg,Object type,boolean saveAsDirectory,Object update,boolean saveLog){
            Object bundle=null;
            Handler romHandler=new Handler();
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            PrintStream verboseLog=new PrintStream(baos);
            try {
                /* PERFORM */
            } finally {System.out.printf("COUNTS %d %d %d%n",romHandler.reads,romHandler.writes,romHandler.saves);}
        }
        public static int invoke(String[] args){
            String sourceRomFilePath=null, outputRomFilePath=args[1], cpgName=null, updateFilePath=null;
            Object cpgType=null; boolean saveAsDirectory=false,saveLog=true; long seed=1;
            Settings settings=new Settings();
            /* INVOKE_TAIL */
    }
    public static void main(String[] commandArgs){scenario=commandArgs[0]; /* EXIT */}
}
'''
    for key, value in {"GUARD": guard, "RESULTS": results, "PICKUP": pickup,
                       "RANDOMIZE": randomize, "COMPLETION": completion,
                       "UTILITIES": utilities, "PERFORM": perform.group(),
                       "INVOKE_TAIL": invoke_tail, "EXIT": exit_call.group()}.items():
        harness = harness.replace("/* " + key + " */", value)
    with tempfile.TemporaryDirectory(prefix="cli-result-host-") as tmp:
        source = Path(tmp) / "CliResultHost.java"
        source.write_text(harness)
        subprocess.run(["javac", "-d", tmp, str(source)], check=True)
        cases = {
            "blocked": (1, "0 0 0", "Pickup Items to Unchanged"),
            "unchanged": (0, "0 0 1", None),
            "vanilla": (0, "1 1 1", None),
            "generic-error": (1, "0 0 0", "Synthetic failure"),
            "save-false": (1, "1 1 1", "Could not save ROM"),
        }
        for name, (code, counts, error) in cases.items():
            output = Path(tmp) / (name + "-synthetic-output")
            result = subprocess.run(["java", "-cp", tmp, "CliResultHost", name, str(output)],
                                    text=True, capture_output=True)
            assert result.returncode == code, (name, result)
            assert "COUNTS " + counts in result.stdout, (name, result.stdout)
            assert ("Randomized successfully!" in result.stdout) == (code == 0), result.stdout
            assert ("ERROR:" in result.stdout) == (code != 0), result.stdout
            assert Path(str(output) + ".log").exists() == (code == 0), name
            if error:
                assert error in result.stdout, result.stdout
            else:
                assert "Pickup Items to Unchanged" not in result.stdout, result.stdout
            print(f"PASS: {name}: process exit {code}, accesses {counts}, success/log publication checked")


if __name__ == "__main__":
    main()
