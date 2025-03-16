(ns cldwalker.structured-chat.gemini
  "Provides structured outputs for gemini - https://ai.google.dev/gemini-api/docs/structured-output?lang=node"
  (:require ["@google/generative-ai" :as gen-ai]
            [cldwalker.structured-chat.llm-provider :as llm-provider]
            [cldwalker.structured-chat.util :as util]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            [promesa.core :as p]))

(def ^:private gemini-prop->malli-type
  {:number :int
   :checkbox :boolean
   ;; doesn't matter since we're overridding it
   :date :any
   ;; TODO: Add support for :datetime
   ;; Gemini doesn't support :format uri so string has to be enough
   :url :string
   :default :string})

(defn- gemini-malli-props [prop-type _options]
  (cond
    ;; Add json-schemable :date via malli property rather fun install fun of malli.experimental.time
    (= :date prop-type)
    {:json-schema {:type "string" :format "date-time"}}
    ;; (= prop-ident :schema.property/url)
    ;; {:json-schema {:type "string" :description "wikipedia url"}}
    ;; (= prop-ident :schema.property/birthPlace)
    ;; {:json-schema {:type "string" :description "country"}}
    ))

(defn- parse-gemini-date [s]
  (let [date-str (or (second (re-find #"(\d{4}-\d{2}-\d{2})" s))
                     (throw (ex-info (str "Invalid date in response: " (pr-str s)) {})))]
    (parse-long (string/replace date-str "-" ""))))

(defn- gemini-chat
  [{{:keys [raw args many-objects]} :user-input :as llm} export-properties]
  (when-not js/process.env.GEMINI_API_KEY
    (util/error "Variable $GEMINI_API_KEY has no value"))
  (let [gen-ai-client (new gen-ai/GoogleGenerativeAI js/process.env.GEMINI_API_KEY)
        schema (llm-provider/generate-json-schema-format llm export-properties)
        prompt (if many-objects
                 (str "Tell me about " (first args) "(s) "
                      (->> (string/split (string/join " " (rest args)) #"\s*,\s*")
                           (map #(pr-str %))
                           (string/join ", ")))
                 (str "Tell me about " (first args) " " (pr-str (string/join " " (rest args)))))
        post-body {:model "gemini-2.0-flash"
                   :generationConfig {:responseMimeType "application/json"
                                      :responseSchema schema}}
        post-body' (clj->js post-body :keyword-fn #(subs (str %) 1))
        model (.getGenerativeModel gen-ai-client post-body')]
    (-> (p/let [result (.generateContent model prompt)
                resp (.-response result)]
          (if raw
            (pprint/pprint (update-in (js->clj resp :keywordize-keys true)
                                      [:candidates 0 :content :parts 0 :text]
                                      #(-> (js/JSON.parse %)
                                           (js->clj :keywordize-keys true))))
            (llm-provider/print-export-map llm (.text resp) export-properties)))
        (p/catch (fn [e]
                   (if (instance? gen-ai/GoogleGenerativeAIFetchError e)
                     (util/error (str "Error: Chat endpoint returned " (.-status e) " with message " (pr-str (.-message e))))
                     (println "Unexpected error: " e)))))))

(defrecord Gemini [user-input]
  llm-provider/LlmProvider
  (chat [this export-properties]
    (gemini-chat this export-properties))
  (property-type-to-malli-type [_] gemini-prop->malli-type)
  (property-to-malli-options [_ prop-type options]
    (gemini-malli-props prop-type options))
  (parse-date-string [_ s] (parse-gemini-date s)))