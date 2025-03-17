## Description

This project provides a CLI for extracting [structured output](https://ollama.com/blog/structured-outputs) from different LLM providers and optionally importing it to [Logseq](https://logseq.com). Currently this works for Google's gemini and Ollama's llama models. To see a demo of how this works with Ollama, see [this video](https://www.loom.com/share/bd98db65474f4e828bd4db65d556159c).

## Setup

* Install dependencies:
     * Install node.js >= 20 and yarn.
     * Run `yarn install` to install npm dependencies.
     * Install [babashka](https://github.com/babashka/babashka).
* Setup a LLM provider:
     * [Gemini](https://ai.google.dev/gemini-api/docs) - Google's models that run online. Results are faster and more accurate and the free tier is generous.
          * Get an api key from https://aistudio.google.com/app/apikey. No credit card is needed.
          * Set `$GEMINI_API_KEY` with the api key
     * [Ollama](https://ollama.com/) - Different open models that run offline. Results are slower and less accurate (unless you have a decked out GPU).
          * [Install ollama](https://ollama.com/download).
          * Download and run the [llama 3.2 model](https://ollama.com/library/llama3.2) with the command: `ollama run llama3.2 --keepalive 1h`. This loads the model so response times are faster for the next hour.
* (Optional) Setup a Logseq graph to use the LLM responses:
     * Use the [DB version of Logseq](https://github.com/logseq/docs/blob/feat/db/db-version.md) at https://test.logseq.com/ or build it from source.
     * Create a Logseq graph with the [import SQlite feature](https://github.com/logseq/docs/blob/feat/db/db-version.md#graph-export) and provide [this schema db file](./schema/db.sqlite). Name the graph `schema`.
     * Another to way create the schema graph is to clone the logseq repository and follow the [scripts/ README](https://github.com/logseq/logseq/tree/feat/db/scripts#usage) instructions for setting up a script and then run the schema.org related script.

## Usage

The script `structured_chat.mjs` queries the LLM to ask about specific things (objects). Let's start by asking about the book [Don Quixote](https://en.wikipedia.org/wiki/Don_Quixote):

```
$ node structured_chat.mjs book don quixote
...
 :pages-and-blocks
 [{:page
   {:block/title "Don Quixote",
    :build/tags [:schema.class/Book],
    :build/keep-uuid? true,
    :build/properties
    {:user.property/importedAt 1740499711013,
     :schema.property/author
     #{[:build/page
        {:block/title "Miguel de Cervantes Sañez",
         :build/tags [:schema.class/Person],
         :build/properties
         {:user.property/importedAt 1740499711013,
          :schema.property/url
          "https://en.wikipedia.org/wiki/Miguel_de_Cervantes"}}]},
     :schema.property/datePublished
     [:build/page {:build/journal 16050124}]}}}]}
```

You can see that we get back the book's published date and the author's name and url. This data is exactly what is asked for in the CLI's default configuration.

Now with this data copied to your clipboard (on osx), let's import it into the Logseq `schema` graph you've created. Run the command `Import EDN Data`, paste the data and Import. Your graph now has a Don Quixote `#Book` page with 2 properties, each which have property values that are pages!

Now that we have some info about the book, let's get more data about its author:

```
$ node structured_chat.mjs person "Miguel de Cervantes Sañez"
...
 :pages-and-blocks
 [{:page
   {:block/title "Miguel de Cervantes Sañez",
    :build/tags [:schema.class/Person],
    :build/keep-uuid? true,
    :build/properties
    {:user.property/importedAt 1740501976762,
     :schema.property/birthDate
     [:build/page {:build/journal 15471129}],
     :schema.property/birthPlace
     #{[:build/page
        {:block/title "Alcalá, Spain",
         :build/tags [:schema.class/Place]}]},
     :schema.property/gender "Male"}}}]}
```

Import this data into your graph and see your existing page about Miguel update with his birth place, birthday and gender! Feel free to query the LLM about any book or person. Sometimes you need to make the same query more than once to get back useful results for all properties.

So far we have been querying [books](https://schema.org/Book) and [people](https://schema.org/Person) classes (tags in Logseq). We got back a number of specific properties because the CLI comes preconfigured for those tags, as well as for [movies](https://schema.org/Movie) and [songs](https://schema.org/MusicRecording).

The CLI can also be used to explore any of [schema.org's 900 tags](https://schema.org/docs/full.html). For example, let's learn about Clojure [software](https://schema.org/SoftwareSourceCode):

```
$ node structured_chat.mjs softwaresourcecode clojure -p author codeRepository
...
 :pages-and-blocks
 [{:page
   {:block/title "Clojure",
    :build/tags [:schema.class/SoftwareSourceCode],
    :build/keep-uuid? true,
    :build/properties
    {:user.property/importedAt 1741017730510,
     :schema.property/author
     #{[:build/page
        {:block/title "Rich Hickey",
         :build/tags [:schema.class/Person],
         :build/properties
         {:user.property/importedAt 1741017730511,
          :schema.property/url "http://www.clojure.org/about/"}}]
       [:build/page
        {:block/title " contributors",
         :build/tags [:schema.class/Person],
         :build/properties
         {:user.property/importedAt 1741017730511,
          :schema.property/url
          "https://github.com/clojure/clojure/blob/master/CREDITS.md"}}]},
     :schema.property/codeRepository
     "https://github.com/clojure/clojure.git",
     :schema.property/url "https://clojure.org/"}}}]}
```

To step back a bit, why does this script use the schema.org ontology? It's because it is a large ontology that has been used by the major search engines for over a decade. In addition to 900 tags, it provides 1400+ [properties](https://meta.schema.org/Property) that are well designed for reuse. To quickly explore a variety of properties, use `-r`. For example, we can redo the Clojure query and ask for 5 random properties associated with it:

```
$ node structured_chat.mjs softwaresourcecode clojure -p author -r 5
To recreate these random properties: -p learningResourceType encodingFormat accessibilitySummary abstract sdPublisher
...
 :pages-and-blocks
 [{:page
   {:block/title "Clojure",
    :build/tags [:schema.class/SoftwareSourceCode],
    :build/keep-uuid? true,
    :build/properties
    {:user.property/importedAt 1741017889361,
     :schema.property/author
     #{[:build/page
        {:block/title "Rich Hickey",
         :build/tags [:schema.class/Person],
         :build/properties
         {:user.property/importedAt 1741017889361,
          :schema.property/url "https://github.com/clojure"}}]},
     :schema.property/learningResourceType "programming language",
     :schema.property/encodingFormat "text/x-clojure",
     :schema.property/accessibilitySummary
     "Clojure is a modern, dynamic, and functional programming language designed to write concurrent and parallel code without compromising performance.",
     :schema.property/abstract
     "Clojure is a compiled, multi-paradigm language that runs on the Java Virtual Machine (JVM). It's based on Lisp syntax and offers features like immutability, recursion, and macros for building domain-specific languages.",
     :schema.property/sdPublisher
     #{[:build/page
        {:block/title "Rich Hickey",
         :build/tags [:schema.class/Person],
         :build/properties
         {:user.property/importedAt 1741017889361,
          :schema.property/url "https://github.com/clojure"}}]},
     :schema.property/url "https://clojure.org/"}}}]}
```

There is more functionality to explore. See additional options with `node structured_chat.mjs -h`

## Configuring the CLI

The CLI is configured with the `default-config` var in [structured_chat.cljs](./src/cldwalker/structured_chat.cljs). Feel free to modify it to add preferred defaults for schema.org classes.

You could also configure the CLI to work with your Logseq graph's ontology. Doing so requires knowing the `:db/ident` of your graph's tags and properties. To get this data, run the dev command `Show page data` and look for the keyword next to `:db/ident`. NOTE: I have not tried this outside of the schema.org ontology and don't know how well LLMs will respond to custom ontologies. I would think schema.org works better than most ontologies because of its widespread adoption and thus nontrivial representation in a LLM's training.