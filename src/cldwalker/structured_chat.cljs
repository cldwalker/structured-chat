(ns cldwalker.structured-chat
  "Main ns for running CLI"
  (:require [babashka.cli :as cli]
            [nbb.error]
            [promesa.core :as p]))

(def ^:private spec
  "Options spec"
  {:help {:alias :h
          :desc "Print help"}
   :block-import {:alias :b
                  :desc "Import object(s) as block(s) in today's journal"}
   :raw {:alias :R
         :desc "Print raw json chat response instead of Logseq EDN"}
   :json-schema-inspect {:alias :j
                         :desc "Print json schema to submit and don't submit to chat"}
   :properties {:alias :p
                :desc "Initial properties to fetch for object(s)"
                :coerce []}
   :global-properties {:alias :P
                       :desc "Global properties to fetch for all objects"
                       :coerce []
                       :default ["url"]}
   :random-properties {:alias :r
                       :desc "Random number of properties to fetch for top-level object(s)"
                       :coerce :long}
   :many-objects {:alias :m
                  :desc "Query is for multiple comma separated objects"}
   :graph {:alias :g
           :desc "Graph to run against. *Required if default not set*"}
   :ollama {:alias :o
            :desc "Use ollama instead of gemini"}})

(defn- lazy-load-fn
  "Lazy load fn to speed up start time. After nbb requires ~30 namespaces, start time gets close to 1s"
  [fn-sym]
  (fn [& args]
    (-> (p/let [_ (require (symbol (namespace fn-sym)))]
          (apply (resolve fn-sym) args))
        (p/catch (fn [err]
                   (if (= :sci/error (:type (ex-data err)))
                     (nbb.error/print-error-report err)
                     (js/console.error "Error:" err))
                   (js/process.exit 1))))))

(defn -main [& args]
  (try
    (let [{options :opts args' :args} (cli/parse-args args {:spec spec})
          ;; TODO: put ./schema in config w/ out loading too much
          graph-dir (or (:graph options) "./schema")
          _ (when (or (nil? graph-dir) (:help options) (nil? (first args)))
              (println (str "Usage: $0 CLASS [& ARGS] [OPTIONS]\nOptions:\n"
                            (cli/format-opts {:spec spec})))
              (js/process.exit 0))]
      ((lazy-load-fn 'cldwalker.structured-chat.main/-main) graph-dir args' options))
    (catch ^:sci/error js/Error e
      (nbb.error/print-error-report e)
      (js/process.exit 1))))

#js {:main -main}