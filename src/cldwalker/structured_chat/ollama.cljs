(ns cldwalker.structured-chat.ollama
  "Provides structured outputs for ollama - https://ollama.com/blog/structured-outputs"
  (:require [cldwalker.structured-chat.llm-provider :as llm-provider]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            [promesa.core :as p]))

(def ^:private ollama-prop->malli-type
  {:number :int
   :checkbox :boolean
   ;; doesn't matter since we're overridding it
   :date :any
   ;; TODO: Add support for :datetime
   :url uri?
   :default :string})

(defn- ollama-malli-props [prop-type _options]
  ;; Add json-schemable :date via malli property rather fun install fun of malli.experimental.time
  (when (= :date prop-type)
    {:json-schema {:type "string" :format "date"}}))

(defn- ->post-body [{{:keys [many-objects]} :user-input :as llm} export-properties args]
  (let [prompt (if many-objects
                 (str "Tell me about " (first args) "(s) "
                      (->> (string/split (string/join " " (rest args)) #"\s*,\s*")
                           (map #(pr-str %))
                           (string/join ", ")))
                 (str "Tell me about " (first args) " " (pr-str (string/join " " (rest args)))))]
    {:model "llama3.2"
     :messages [{:role "user" :content prompt}]
     :stream false
     :format (llm-provider/generate-json-schema-format llm export-properties)}))

(defn- ollama-chat
  [{{:keys [args raw]} :user-input :as llm} export-properties]
  (let [post-body (->post-body llm export-properties args)
        post-body' (clj->js post-body :keyword-fn #(subs (str %) 1))]
    ;; TODO: Try javascript approach for possibly better results
    ;; Uses chat endpoint as described in https://ollama.com/blog/structured-outputs
    (-> (p/let [resp (js/fetch "http://localhost:11434/api/chat"
                               #js {:method "POST"
                                    :headers #js {"Accept" "application/json"}
                                    :body (js/JSON.stringify post-body')})
                body (.json resp)]
          (if (= 200 (.-status resp))
            (if raw
              (pprint/pprint (update-in (js->clj body :keywordize-keys true)
                                        [:message :content]
                                        #(-> (js/JSON.parse %)
                                             (js->clj :keywordize-keys true))))
              (llm-provider/print-export-map llm (.. body -message -content) export-properties))
            (do
              (println "Error: Chat endpoint returned" (.-status resp) "with message" (pr-str (.-error body)))
              (js/process.exit 1))))
        (p/catch (fn [e]
                   (println "Unexpected error: " e))))))

(defrecord Ollama [user-input]
  llm-provider/LlmProvider
  (chat [this export-properties]
    (ollama-chat this export-properties))
  (property-type-to-malli-type [_] ollama-prop->malli-type)
  (property-to-malli-options [_ prop-type options]
    (ollama-malli-props prop-type options))
  (parse-date-string [_ s]
    (parse-long (string/replace s "-" ""))))