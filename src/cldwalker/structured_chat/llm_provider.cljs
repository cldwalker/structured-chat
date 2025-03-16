(ns cldwalker.structured-chat.llm-provider
  "Provides llm provider agonistic actions for creating a structured chat and responding to it"
  (:require [cldwalker.structured-chat.util :as util]
            [cljs.pprint :as pprint]
            [logseq.common.util :as common-util]
            [logseq.common.util.date-time :as date-time-util]
            [logseq.db.sqlite.export :as sqlite-export]
            [malli.json-schema :as json-schema]))

(defprotocol LlmProvider
  (chat [this export-properties])
  (property-type-to-malli-type [this])
  (property-to-malli-options [this prop-type options])
  (parse-date-string [this s]))

(defn- ->property-value-schema
  "Returns a vec of optional malli properties and required schema for a given property ident"
  [{{:keys [input-class input-global-properties user-config]} :user-input :as llm} export-properties prop-ident]
  (let [prop->malli-type (property-type-to-malli-type llm)
        prop-type (get-in export-properties [prop-ident :logseq.property/type])
        schema* (if (= :node prop-type)
                  (let [obj-properties (->> (get-in user-config [input-class :properties prop-ident :chat/properties])
                                            (concat input-global-properties)
                                            distinct)]
                    (into
                     [:map [:name {:min 2} :string]]
                     (map #(apply vector % (->property-value-schema llm export-properties %))
                          obj-properties)))
                  (get prop->malli-type prop-type))
        _ (assert schema* (str "Property type " (pr-str prop-type) " must have a schema type"))
        schema (if (= :db.cardinality/many (get-in export-properties [prop-ident :db/cardinality]))
                 [:sequential {:min 1} schema*]
                 schema*)
        malli-options (property-to-malli-options llm prop-type {})
        props-and-schema (cond-> []
                           (some? malli-options)
                           (conj malli-options)
                           true
                           (conj schema))]
    props-and-schema))

(defn- ->query-schema [{{:keys [input-class input-properties input-global-properties many-objects user-config]} :user-input :as llm}
                       export-properties]
  (let [schema
        (into
         [:map [:name {:min 2} :string]]
         (map
          (fn [k]
            (apply vector
                   (or (get-in user-config [input-class :properties k :chat-ident]) k)
                   (->property-value-schema llm export-properties k)))
          (distinct
           (concat (:chat/class-properties (input-class user-config))
                   input-properties
                   input-global-properties))))]
    (if many-objects
      [:sequential {:min 1} schema]
      schema)))

(defn generate-json-schema-format
  "Given a llm object return the json schema for it"
  [llm export-properties]
  ;; (pprint/pprint (->query-schema llm export-properties options))
  (json-schema/transform (->query-schema llm export-properties)))

(defn- buildable-properties [{{:keys [input-class user-config]} :user-input :as llm} properties export-properties]
  (->> properties
       (map (fn [[chat-ident v]]
              (let [prop-ident (or (some (fn [[k' v']] (when (= chat-ident (:chat-ident v')) k'))
                                         (:properties (get user-config input-class)))
                                   chat-ident)]
                [prop-ident
                 (let [prop-value
                       (fn [e]
                         (case (get-in export-properties [prop-ident :logseq.property/type])
                           :node
                           [:build/page (let [obj-tags (or (get-in user-config [input-class :properties prop-ident :build/tags])
                                                           (some-> (get-in export-properties [prop-ident :build/property-classes])
                                                                   (subvec 0 1)))]
                                          (cond-> {:block/title (:name e)}
                                            (seq obj-tags)
                                            (assoc :build/tags obj-tags)
                                            (seq (dissoc e :name))
                                            (assoc :build/properties
                                                   (buildable-properties llm (dissoc e :name) export-properties))))]
                           :date
                           [:build/page {:build/journal (parse-date-string llm e)}]
                           (:number :checkbox)
                           e
                           (:default :url)
                           (str e)))]
                   (if (vector? v)
                     (set (map prop-value v))
                     (prop-value v)))])))
       (into {:user.property/importedAt (common-util/time-ms)})))

(defn print-export-map
  "Given a llm object and a json response, print and optional copy an export map"
  [{{:keys [input-class block-import many-objects user-config]} :user-input :as llm} content-json export-properties]
  (let [content (-> (js/JSON.parse content-json)
                    (js->clj :keywordize-keys true))
        objects (mapv #(hash-map :name (:name %)
                                 :properties (buildable-properties llm (dissoc % :name) export-properties))
                      (if many-objects content [content]))
        export-classes (merge (zipmap (distinct
                                       (concat (mapcat :build/tags (vals (:properties (get user-config input-class))))
                                               ;; We may not use all of these but easier than walking build/page's
                                               (mapcat :build/property-classes (vals export-properties))))
                                      (repeat {}))
                              {input-class {}})
        pages-and-blocks*
        (if block-import
          [{:page {:build/journal (date-time-util/date->int (new js/Date))}
            :blocks (mapv (fn [obj]
                            {:block/title (:name obj)
                             :build/tags [input-class]
                             :build/properties (:properties obj)})
                          objects)}]
          (mapv (fn [obj]
                  {:page
                   {:block/title (:name obj)
                    :build/tags [input-class]
                   ;; Allows upsert of existing page
                    :build/keep-uuid? true
                    :build/properties (:properties obj)}})
                objects))
        {:keys [pages-and-blocks invalid-urls urls]} (util/remove-invalid-properties pages-and-blocks*)
        export-map
        {:properties (merge export-properties
                            {:user.property/importedAt
                             {:logseq.property/type :datetime
                              :db/cardinality :db.cardinality/one}})
         :classes export-classes
         :pages-and-blocks pages-and-blocks}]
    (#'sqlite-export/ensure-export-is-valid export-map)
    (pprint/pprint export-map)
    (when (util/command-exists? "pbcopy")
      (util/copy-to-clipboard (with-out-str (pprint/pprint export-map))))
    (when (seq invalid-urls)
      (println (str (count invalid-urls) "/" (count urls)) "urls were removed for being invalid:" (pr-str (vec invalid-urls))))))