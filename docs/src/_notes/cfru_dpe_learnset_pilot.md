# CFRU/DPE pilot: preserve native level-up learnsets

For the selected extended CFRU/DPE Gen-9 profile, choose **Movesets: Unchanged**,
leave **Reorder Damaging Moves** off, and do not select **Metronome Only**.
These conditions also apply to imported settings and presets. Other moveset modes
and direct level-up learnset writes are rejected; they require a separately
verified allocator and are outside this pilot. Move-related settings that only
read learnsets or modify other data are not enabled or validated by this rule.

This path avoids the existing level-up learnset repoint writer and preserves the
installed level-up tables and assets with respect to that writer. It does not
prove that other enabled options have safe ownership or that a particular output
or gameplay is accepted. Intro, Evolution, Pickup, graphics and other selected
options retain their own checks and independent evidence requirements. In
particular, CFRU #78 Easier requires matching Evolution and Oak ownership
witnesses; the Oak witness can still reject owners overlapping the old learnset
arena even when learnsets are unchanged. Preserving the older #77 witness does
not authorize a #78 output with a #77 witness.

This is source-only and synthetic evidence. Actual selected output, unchanged
native tables and assets, and gameplay require a separately authorized user-only
validation. No safe allocation range or full D1 acceptance is claimed.
