(ns law.narrative
  "Contracts and schemas for the narrative presence layer.
   The narrator is an interpretive layer over the event ledger; these schemas
   govern the state the mood/utterance systems read and write.")

(def mood-schema
  "Keyword mood tags that the narrative system can assign to the observer."
  #{:wonder :dread :tenderness :sterility :anticipation})

(def narrative-state-schema
  "Observer-side narrative state. `:mood` is the current ambience; the other
   keys are reserved for Phase 2+ embedded phrasing and topic tracking."
  [:map
   [:mood mood-schema]
   [:last-utterance-tick [:or :nil :int]]
   [:topics [:set :keyword]]])

(def utterance-schema
  "A single narrator utterance. Reserved for Phase 2+ embedded phrasing."
  [:map
   [:text :string]
   [:attribution [:enum :ambient :embedded :addressed]]
   [:topic :keyword]
   [:tick :int]
   [:context :map]])

(def topic-schema
  "Narrative topic tags. Reserved for Phase 2+ embedded phrasing."
  #{:collapse :ignition :disc :planet :decoherence :drift})
