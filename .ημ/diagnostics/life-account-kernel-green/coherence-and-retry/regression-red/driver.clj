(require '[clojure.java.io :as io])
(import '[java.security MessageDigest] '[java.nio.file Files])
(doseq [[resource expected-path expected-sha]
 [  ["law/life_account.clj" "/home/err/spaces/foresight/.worktrees/truth-life-actor-plan/.ημ/diagnostics/life-account-kernel-green/coherence-and-retry/pre-fix/src/law/life_account.clj" "f235fb152a004d87aff872ce0a74fe843309a1a1433e0f4f59271a7cbca964ce"]
  ["domain/life_account.clj" "/home/err/spaces/foresight/.worktrees/truth-life-actor-plan/.ημ/diagnostics/life-account-kernel-green/coherence-and-retry/pre-fix/src/domain/life_account.clj" "cf8d55352f0c5dc2719858516a29d1206f9c568f5ff86ce13c726c8897b1b87c"]
  ["domain/life_account_test.clj" "/home/err/spaces/foresight/.worktrees/truth-life-actor-plan/test/domain/life_account_test.clj" "36a3bac05c70abe42d71135a38a5f610bace0549fb74d1ce741d1a01e32d17ec"]]]
  (let [resolved (io/resource resource)
        actual-file (some-> resolved io/file .getCanonicalFile)
        expected-file (.getCanonicalFile (io/file expected-path))
        bytes (when actual-file (Files/readAllBytes (.toPath actual-file)))
        actual-sha (when bytes
                     (format "%064x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256") bytes))))]
    (when-not (and (= expected-file actual-file) (= expected-sha actual-sha))
      (throw (ex-info "Regression classpath mismatch" {:resource resource :actual (str actual-file) :sha actual-sha})))
    (prn {:verified-resource resource :path (str actual-file) :sha256 actual-sha})))
(require '[cognitect.test-runner :as runner])
(runner/-main "-n" "domain.life-account-test"
              "-v" "domain.life-account-test/supplied-history-cannot-be-newer-closed-or-from-different-opening-material"
              "-v" "domain.life-account-test/rejected-unordered-payloads-retain-raw-bits-in-members-and-keys")
