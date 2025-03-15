(ns cldwalker.structured-chat
  (:require ["os" :as os]
            ["path" :as node-path]
            [babashka.cli :as cli]
            [cldwalker.structured-chat.llm-provider :as llm-provider]
            [cldwalker.structured-chat.ollama :as ollama]
            [cldwalker.structured-chat.gemini :as gemini]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            [datascript.core :as d]
            [logseq.db :as ldb]
            #_:clj-kondo/ignore
            [logseq.db.sqlite.cli :as sqlite-cli]))

(def user-config
  "Configure what to query per class/tag. Each class has a map consisting of the following keys:
   * :chat/class-properties - a vec of properties to fetch for this class
   * :properties - Configures properties. Keys are the property kw idents and the values are a map with the keys:
     * :build/tags - For :node properties, tags to set for the property value(s)
     * :chat/properties - For :node properties, properties to fetch for the property value(s)"
  {:schema.class/Movie
   {:chat/class-properties [:schema.property/actor :schema.property/director :schema.property/musicBy
                            :schema.property/datePublished :schema.property/url]
    :properties
    {:schema.property/actor
     {:chat/properties [:schema.property/birthDate #_:schema.property/birthPlace #_:schema.property/character #_:schema.property/hasOccupation]}
     :schema.property/musicBy
     {:build/tags [:schema.class/MusicGroup]}}}

   :schema.class/Book
   {:chat/class-properties [:schema.property/author :schema.property/datePublished :schema.property/url
                            #_#_:schema.property/abridged :schema.property/numberOfPages]
    :properties
    {:schema.property/author
     {:build/tags [:schema.class/Person]}}}

   :schema.class/Person
   {:chat/class-properties [:schema.property/birthDate :schema.property/birthPlace :schema.property/hasOccupation]
    ;; TODO: Get back a more specific place e.g. Country
    #_:properties
    #_{:schema.property/birthPlace
       {:chat-ident :birthCountry :build/tags [:schema.class/Country]
        :chat/properties [:schema.property/additionalType]}}}

   :schema.class/Organization
   {:chat/class-properties [:schema.property/url :schema.property/foundingLocation :schema.property/alumni]}

   :schema.class/MusicRecording
   {:chat/class-properties [:schema.property/byArtist :schema.property/inAlbum :schema.property/datePublished
                            :schema.property/url]
    :properties
    {:schema.property/byArtist
     {:build/tags [:schema.class/MusicGroup]}}}})

(defn- get-dir-and-db-name
  "Gets dir and db name for use with open-db!"
  [graph-dir]
  (if (string/includes? graph-dir "/")
    (let [graph-dir'
          (node-path/join (or js/process.env.ORIGINAL_PWD ".") graph-dir)]
      ((juxt node-path/dirname node-path/basename) graph-dir'))
    [(node-path/join (os/homedir) "logseq" "graphs") graph-dir]))

(def spec
  "Options spec"
  {:help {:alias :h
          :desc "Print help"}
   :block-import {:alias :b
                  :desc "Import object as block in today's journal"}
   :raw {:alias :R
         :desc "Print raw json chat response instead of Logseq EDN"}
   :json-schema-inspect {:alias :j
                         :desc "Print json schema to submit and don't submit to chat"}
   :properties {:alias :p
                :desc "Properties to fetch for top-level object"
                :coerce []}
   :global-properties {:alias :P
                       :desc "Global properties to fetch for all objects"
                       :coerce []
                       :default ["url"]}
   :random-properties {:alias :r
                       :desc "Random number of properties to fetch for top-level object"
                       :coerce :long}
   :many-objects {:alias :m
                  :desc "Query is for multiple comma separated objects"}
   :gemini {:alias :g
            :desc "Run gemini instead of ollama"}})

(defn- error [msg]
  (println (str "Error: " msg))
  (js/process.exit 1))

(defn- translate-input-property [input]
  (if (= "description" input) :logseq.property/description (keyword "schema.property" input)))

(defn- get-class-properties
  [class]
  (let [class-parents (ldb/get-classes-parents [class])]
    (->> (mapcat (fn [class]
                   (:logseq.property.class/properties class))
                 (concat [class] class-parents))
         (map :db/ident)
         distinct)))

(defn -main [& args]
  (let [{options :opts args' :args} (cli/parse-args args {:spec spec})
        [graph-dir & args''] args'
        _ (when (or (nil? graph-dir) (:help options))
            (println (str "Usage: $0 GRAPH-NAME TAG [& ARGS] [OPTIONS]\nOptions:\n"
                          (cli/format-opts {:spec spec})))
            (js/process.exit 1))
        [dir db-name] (get-dir-and-db-name graph-dir)
        conn (sqlite-cli/open-db! dir db-name)
        input-class-ent
        (or (->>
             (d/q '[:find [?b ...]
                    :in $ ?name
                    :where [?b :block/tags :logseq.class/Tag] [?b :block/name ?name]]
                  @conn
                  (string/lower-case (first args'')))
             first
             (d/entity @conn))
            (error (str "No class found for" (pr-str (first args'')))))
        input-class (:db/ident input-class-ent)
        random-properties (when (:random-properties options)
                            (take (:random-properties options)
                                  (shuffle (get-class-properties input-class-ent))))
        _ (when (seq random-properties)
            (println "To recreate these random properties: -p"
                     (string/join " " (map name random-properties))))
        input-map (merge
                   (select-keys options [:many-objects :block-import :raw])
                   {:input-class input-class
                    :input-properties (into (mapv translate-input-property (:properties options))
                                            random-properties)
                    :args args''
                    :user-config user-config
                    :input-global-properties
                    (mapv translate-input-property
                          ;; Use -P to clear default
                          (if (= (:global-properties options) [true]) [] (:global-properties options)))})
        export-properties (->> (:chat/class-properties (input-class user-config))
                               (concat (mapcat :chat/properties (vals (:properties (get user-config input-class)))))
                               (concat (:input-properties input-map))
                               (concat (:input-global-properties input-map))
                               distinct
                               (map #(or (d/entity @conn %)
                                         (error (str "No property exists for " (pr-str %)))))
                               (map #(vector (:db/ident %)
                                             (cond-> (select-keys % [:logseq.property/type :db/cardinality])
                                               (seq (:logseq.property/classes %))
                                               (assoc :build/property-classes
                                                      (mapv :db/ident (:logseq.property/classes %))))))
                               (into {}))
        llm (if (:gemini options) (gemini/->Gemini input-map) (ollama/->Ollama input-map))]
    (if (:json-schema-inspect options)
      (pprint/pprint (llm-provider/generate-json-schema-format llm export-properties))
      (llm-provider/chat llm export-properties))))

#js {:main -main}