(ns arcadia.internal.variables
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string])
  (:use arcadia.core))

(defn type->string [t]
    (if (list? t)
      (pr-str (interpose (symbol ",") t))
      (pr-str t)))

(defn string-format [v]
  (if (list? v)
    "export %s var %s"
    "export (%s) var %s"))

(defn variables-directory []
  (Godot.ProjectSettings/GlobalizePath "res://Variables"))

(defn create-variables-directory! []
  (-> (variables-directory)
      (System.IO.Directory/CreateDirectory)))

(defn variable-file-directory [file]
  (-> (variables-directory)
      (System.IO.Path/Combine (string/replace file #"res://" ""))
      (System.IO.Path/GetDirectoryName)))

(defn variable-file [file]
  (string/replace
   (->> file
        (Godot.ProjectSettings/GlobalizePath)
        (System.IO.Path/GetFileName)
        (System.IO.Path/Combine (variable-file-directory file)))
   #"\.tscn"
   ".gd"))

(defn var->line [[k v]]
  (format (string-format v) (type->string v) (string/replace (name k) #"-" "_")))

(defn read-variables-edn []
  ;; OPTIMIZATION: load this into memory?
  (-> "res://variables.edn"
      (Godot.ProjectSettings/GlobalizePath)
      (slurp)
      (edn/read-string)))

(defn generate-variables! []
  (create-variables-directory!)
  (doseq [[scene-file vars] (read-variables-edn)]
    (let [directory (variable-file-directory scene-file)]
      (System.IO.Directory/CreateDirectory directory)
      (->> (mapv var->line vars)
           (cons ["# This file is generated by ArcadiaGodot"
                  "# By namespace: arcadia.internal.variables"
                  "# Do not edit directly"
                  "extends Node"])
           (flatten)
           (string/join "\n")
           (spit (variable-file scene-file))))))

(defn connect-variables! []
  (create-variables-directory!)
  (doseq [[scene-file _] (read-variables-edn)]
    (let [packed-scene (Godot.ResourceLoader/Load scene-file "PackedScene" false)
          scene-instance (.Instance packed-scene 0)
          script (Godot.ResourceLoader/Load (variable-file scene-file) "GDScript" false)]
      (when-not (.GetScript scene-instance)
        (.SetScript scene-instance script)
        (.Pack packed-scene scene-instance)
        (Godot.ResourceSaver/Save scene-file packed-scene (Arcadia.Util/FLAG_RELATIVE_PATHS))))))

(defn set-variable-state [self]
  (when-let [vars (get (read-variables-edn) (.Filename self))]
    (doseq [[k _] vars]
      (let [v (.Get self (string/replace (name k) #"-" "_"))]
        (update-state self #(cond-> % (not (k %)) (assoc k v))))))
  self)
