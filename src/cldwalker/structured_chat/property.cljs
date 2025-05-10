(ns cldwalker.structured-chat.property
  "This CLI's Logseq properties and any related fns")

(def properties
  "This CLI's Logseq properties"
  ;; Properties on imported things
  {::importedAt
   {:logseq.property/type :datetime
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Time when something is imported into Logseq"}}
   ::llmResponse
   {:logseq.property/type :node
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "The LLM Response"}}

  ;; LLMResponse properties
   ::prompt
   {:logseq.property/type :default
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Full prompt used to generate response"}}
   ::llmProvider
   {:logseq.property/type :default
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "LLM provider for a response"}}
   ::model
   {:logseq.property/type :default
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "LLM Model of a provider"}}
   ::totalTime
   {:logseq.property/type :number
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Total time to generate and receive response (ms)"}}
   ::tokens
   {:logseq.property/type :number
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Response's tokens"}}})