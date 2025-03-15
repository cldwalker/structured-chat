(ns cldwalker.structured-chat.util
  "Misc fns"
  (:require ["child_process" :as child-process]
            [clojure.walk :as walk]))

(defn copy-to-clipboard [text]
  (let [proc (child-process/exec "pbcopy")]
    (.write (.-stdin proc) text)
    (.end (.-stdin proc))))

(defn command-exists? [command]
  (try
    (child-process/execSync (str "which " command) #js {:stdio "ignore"})
    true
    (catch :default _ false)))

(defn remove-invalid-properties [pages-and-blocks*]
  (let [urls (atom #{})
        _ (walk/postwalk (fn [f]
                           (when (and (vector? f) (= :schema.property/url (first f)))
                             (swap! urls conj (second f)))
                           f)
                         pages-and-blocks*)
        ;; Won't need regex check if ollama ever supports regex 'pattern' option
        invalid-urls (remove #(re-find #"^(https?)://.*$" %) @urls)
        pages-and-blocks (walk/postwalk (fn [f]
                                          (if (and (map? f) (contains? (set invalid-urls) (:schema.property/url f)))
                                            (dissoc f :schema.property/url)
                                            f))
                                        pages-and-blocks*)]
    {:pages-and-blocks pages-and-blocks
     :urls @urls
     :invalid-urls invalid-urls}))
