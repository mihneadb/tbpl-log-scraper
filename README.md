# log-scraper

A Clojure tool for downloading tbpl [logs](http://ftp.mozilla.org/pub/mozilla.org/firefox/tinderbox-builds/mozilla-inbound-linux-debug/).

## Usage

`lein run <path to download-dir>`

Or you can compile it and just run it as a java app.

  lein uberjar

  java -jar target/log-scraper-0.1.0-SNAPSHOT-standalone.jar <path to download-dir>

