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
  "seq of inbound slave directories available at base-url"
  (map #(str base-url %) (map extract-href (filter is-inbound? (extract-links (fetch-url base-url))))))

(defn extract-build-dirs [dir]
  "Seq with urls of the build directories available in a slave dir."
  (butlast (map #(str dir (extract-href %)) (rest (extract-links (fetch-url dir))))))

(defn extract-logs [bdir]
  "Seq of log urls for a given build dir."
  (map #(str bdir %) (map extract-href (filter is-log? (extract-links (fetch-url bdir))))))

(defn remove-last-char [s]
  (.substring s 0 (dec (count s))))

(defn basename [url]
  "/hello/how/are/you -> you"
  (last (string/split url #"/")))

(defn slash-join [& args]
  (string/join "/" args))

(defn mkdir [path]
  (.mkdir (java.io.File. path)))

(defn exists? [path]
  (.exists (java.io.File. path)))

(defn download-log [log local-log-dir]
  "Downloads the given log to local-log dir if it doesn't already exist there"
  (let [local-log-file (slash-join local-log-dir (basename log))]
    (when-not (exists? local-log-file)
      (download-url-to log local-log-file))))

(defn download-build-dir [bdir local-slave-dir]
  "Downloads a build dir (like mozilla-inbound-win32/1374544128) inside local-slave-dir"
  (let [local-log-dir (slash-join local-slave-dir (basename bdir))]
    (mkdir local-log-dir)
    (doseq [log (extract-logs bdir)]
      (download-log log local-log-dir))))

(defn print-progress [crt total]
  (printf "\tDone: %d/%d\r" crt total)
  (flush))

(defn scrape-slave-logs [slave-dir download-dir]
  "Downloads all logs from the slave-dir to the given directory."
  (println "Scraping" slave-dir)
  (let [local-slave-dir (slash-join download-dir (basename slave-dir))
        build-dirs (extract-build-dirs slave-dir)
        total-count (count build-dirs)
        done (atom 0)]
    (mkdir local-slave-dir)
    (doseq [bdir build-dirs]
      (download-build-dir bdir local-slave-dir)
      (swap! done inc)
      (print-progress @done total-count))))


(defn -main [& args]
  (if (< (count args) 1)
    (println "Need to pass in a path for download dir.")
    (do
      (.mkdir (java.io.File. (first args)))
      (when (= 2 (count args))
        (def inbound-dirs (filter #(.endsWith (remove-last-char %) (second args)) inbound-dirs)))
      (println "Downloading logs from")
      (dorun (map #(println "  " (basename %)) inbound-dirs))
      (dorun (pmap #(scrape-slave-logs % (first args)) inbound-dirs))
      (println "Done."))))
