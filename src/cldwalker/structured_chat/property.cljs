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
   {:logseq.property/type :string
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Full prompt used to generate response"}}
   ::llmProvider
   {:logseq.property/type :string
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "LLM provider for a response"}}
   ::model
   {:logseq.property/type :string
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "LLM Model of a provider"}}
   ::tokens
   {:logseq.property/type :integer
    :db/cardinality :db.cardinality/one
    :build/properties
    {:logseq.property/description "Response's tokens"}}})