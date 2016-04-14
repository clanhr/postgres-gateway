(defproject clanhr/postgres-gateway "1.7.0"
  :description "ClanHR postgres-gateway"
  :url "https://github.com/clanhr/postgres-gateway"

  :license {:name         "The MIT License"
            :url          "file://LICENSE"
            :distribution :repo
            :comments     "Copyright Selfcare All Rights Reserved."}

  :min-lein-version "2.5.0"

  :dependencies.edn "https://raw.githubusercontent.com/clanhr/dependencies/master/dependencies.edn"

  :dependency-sets [:clojure :common :clanhr]

  :dependencies [[org.clojure/java.jdbc "0.5.8"]
                 [alaisi/postgres.async "0.6.0" :exclusions [[io.netty/netty-handler]]]
                 [postgresql "9.3-1102.jdbc41"]]

  :plugins [[clanhr/shared-deps "0.2.6"]
            [lein-environ "1.0.0"]
            [lein-ancient "0.6.5"]]

  :source-paths ["src"]
  :test-paths ["test"])

