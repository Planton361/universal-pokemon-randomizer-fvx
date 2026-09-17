# CFRU/DPE Pickup and CLI result gate

The recognized CFRU/DPE profile has engine-owned, level-dependent Pickup
arrays. The FRLG legacy table is not a supported writer for that profile.
Both ROM-layer entry points reject it before metadata/table access. Set
**Pickup Items to Unchanged** for this pilot.

The follow-up to candidate `0df4ed3d83fba6176f19a3c3146bd3c27561e47e`
repairs a generic CLI propagation defect: `GameRandomizer.randomize` catches
exceptions and returns `Results`; the CLI previously discarded that result.
The CLI now tests `wasSaveSuccessful()` before publishing a log or announcing
success. Failure prints the captured exception, returns false to `invoke`,
and follows the existing `usageError` return of 1. `RandomizerLauncher` passes
that value to `System.exit`. A false `saveRom` result follows the same path.
This does not redesign randomization or add a Pickup-specific CLI branch.
No output is deleted or rolled back; this is a reporting gate, not a general
transactional writer. Pickup rejection happens before `saveRom` is reached.

ROM-free checks:

```sh
python3 romio/src/test/host/check_cfru_dpe_pickup_guard.py
python3 random/src/test/host/check_cli_result_propagation.py
git diff --check
```

Both host checks passed on installed Java 23. The second executes extracted
production Results/catch, Pickup setting dispatch, completion, CLI return
tail and launcher exit expression against synthetic services. It asserts
exit 1 / no success / no published log / no Pickup accesses for rejection;
exit 0 / no warning / no Pickup accesses for Unchanged; vanilla accesses
preserved; and correct propagation of another exception and false save.
It does not exercise ROM opening or real settings parsing.

`CliCompletionTest` adds JUnit coverage using actual Results objects, including
preserving an existing log on failure. `Gen3CfruDpePickupGuardTest` retains the
ROM-layer guard coverage. Full module/JUnit validation remains pending the
approved repository toolchain (Java 25, Gradle 9.3.1); none was installed here:

```sh
gradle :romio:test --tests '*Gen3CfruDpePickupGuardTest' :romio:build
gradle :random:test --tests '*CliCompletionTest' :random:build
```

The build config uses `ignoreFailures = true`: inspect the test result counts,
not just the Gradle exit code. Avoid ROM-fixture tests. Final user-side smoke:
recognized pilot + Random Pickup must fail with the Unchanged instruction,
exit 1 and no successful output/log; Unchanged must succeed silently; vanilla
Random Pickup must retain its normal behavior. No ROM/save/state was used for
this change. Draft only; no merge or component pin change.
