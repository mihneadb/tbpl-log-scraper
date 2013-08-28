(ns log-scraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class :main true))

(def base-url "http://ftp.mozilla.org/pub/mozilla.org/firefox/tinderbox-builds/")

(defn fetch-url
  [url]
  (html/html-resource (java.net.URL. url)))

(defn extract-href
  [elem]
  (-> elem :attrs :href))

(defn extract-links
  [page]
  (html/select page [:td :a]))

(defn is-inbound?
  [elem]
  (.startsWith (html/text elem) "mozilla-inbound"))

(defn is-log?
  [elem]
  (.endsWith (html/text elem) ".txt.gz"))

(defn download-url-to
  [url save-path]
  (with-open [in (io/input-stream url)]
    (io/copy in (io/file save-path))))

(def inbound-dirs
  (map #(str base-url %) (map extract-href (filter is-inbound? (extract-links (fetch-url base-url))))))

(defn extract-build-dirs
  "Returns the build directories' urls for a given dir."
  [dir]
  (butlast (map #(str dir (extract-href %)) (rest (extract-links (fetch-url dir))))))

(defn extract-logs
  "List of log urls for a given build dir."
  [bdir]
  (map #(str bdir %) (map extract-href (filter is-log? (extract-links (fetch-url bdir))))))

(defn basename
  [url]
  (last (string/split url #"/")))

(defn scrape-builder-logs
  "Downloads all logs from the builder-dir to the given directory."
  [builder-dir download-dir]
  (let [local-dir (str download-dir "/" (basename builder-dir))
        process-bdir (fn [bdir]
                      (let [local-build-dir (str local-dir "/" (basename bdir))]
                        (.mkdir (java.io.File. local-build-dir))
                        (doseq [log (extract-logs bdir)]
                          (download-url-to log (str local-build-dir "/" (basename log))))))]
    (.mkdir (java.io.File. local-dir))
    (dorun (pmap process-bdir (extract-build-dirs builder-dir)))))


(defn -main
  [& args]
  (if (not= 1 (count args))
    (println "Need to pass in a path for download dir.")
    (do
      (.mkdir (java.io.File. (first args)))
      (println "Starting..")
      (scrape-builder-logs (first inbound-dirs) (first args))
      (println "Done."))))
