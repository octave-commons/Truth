# Baseline attribution correction

Independent review after baseline commit `8f953d2bcfab2fa2b3fef8a390157a4629eab9e0`
found one wording error in `baseline/RESULT.md`: its phrase “isolated profile”
refers to the prior **active-load native JFR observation** recorded in
`truth-native-profile/.ημ/diagnostics/native-profile/RESULT.md`, not an isolated
native profile. The 86/1858 helper sample attribution is unchanged. It motivates
measurement; it predicts neither collector speedup nor native FPS.

The six newly recorded headless baseline cases remain separately measured with
the owned native process suspended and resumed as documented. Their intervals,
raw results, frozen behavior, and baseline commit remain unchanged. Historical
hashes are retained; this correction supersedes only that attribution wording.
