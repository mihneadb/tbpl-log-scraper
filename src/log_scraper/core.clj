(ns log-scraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class :main true))

(def base-url "http://ftp.mozilla.org/pub/mozilla.org/firefox/tinderbox-builds/")

(defn fetch-url [url]
  "enlive html repr of the given url"
  (html/html-resource (java.net.URL. url)))

(defn extract-href [elem]
  "String value of the given elem's href"
  (-> elem :attrs :href))

(defn extract-links [page]
  "Seq of the link elements on the page"
  (html/select page [:td :a]))

(defn is-inbound? [elem]
  "Does the elem point to an inbound build dir?"
  (.startsWith (html/text elem) "mozilla-inbound"))

(defn is-log? [elem]
  "Does the elem point to a log file?"
  (.endsWith (html/text elem) ".txt.gz"))

(defn download-url-to [url save-path]
  "Download file at given url to save-path"
  (with-open [in (io/input-stream url)]
    (io/copy in (io/file save-path))))

(def inbound-dirs
  "seq of inbound builder directories available at base-url"
  (map #(str base-url %) (map extract-href (filter is-inbound? (extract-links (fetch-url base-url))))))

(defn extract-build-dirs [dir]
  "Seq with urls of the build directories available in a builder dir."
  (butlast (map #(str dir (extract-href %)) (rest (extract-links (fetch-url dir))))))

(defn extract-logs [bdir]
  "Seq of log urls for a given build dir."
  (map #(str bdir %) (map extract-href (filter is-log? (extract-links (fetch-url bdir))))))

(defn basename [url]
  "/hello/how/are/you -> you"
  (last (string/split url #"/")))

(defn scrape-builder-logs [builder-dir download-dir]
  "Downloads all logs from the builder-dir to the given directory."
  (let [local-dir (str download-dir "/" (basename builder-dir))
        process-bdir (fn [bdir]
                      (let [local-build-dir (str local-dir "/" (basename bdir))]
                        (.mkdir (java.io.File. local-build-dir))
                        (doseq [log (extract-logs bdir)]
                          (download-url-to log (str local-build-dir "/" (basename log))))))]
    (.mkdir (java.io.File. local-dir))
    (dorun (pmap process-bdir (extract-build-dirs builder-dir)))))


(defn -main [& args]
  (if (not= 1 (count args))
    (println "Need to pass in a path for download dir.")
    (do
      (.mkdir (java.io.File. (first args)))
      (println "Starting..")
      (scrape-builder-logs (first inbound-dirs) (first args))
      (println "Done."))))
