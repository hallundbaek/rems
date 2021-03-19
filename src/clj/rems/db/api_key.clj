(ns rems.db.api-key
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.util :refer [update-present]]))

(defn- format-api-key [key]
  (-> key
      (update-present :users json/parse-string)
      (update-present :paths json/parse-string)))

(defn get-api-key [key]
  (format-api-key (db/get-api-key {:apikey key})))

(defn get-api-keys []
  (mapv format-api-key (db/get-api-keys {})))

(defn- method= [method pattern]
  (or (= pattern "any")
      (= pattern (name method))))

(defn- allowed-by [method path pattern]
  (and
   (method= method (:method pattern))
   (re-matches (re-pattern (:path pattern)) path)))

(deftest test-allowed-by
  (testing "simple pattern"
    (is (allowed-by :get "/foo" {:method "get" :path "/foo"}))
    (testing ", trailing slashes matter"
      (is (not (allowed-by :get "/foo/" {:method "get" :path "/foo"})))
      (is (not (allowed-by :get "/foo" {:method "get" :path "/foo/"}))))
    (is (not (allowed-by :put "/foo" {:method "get" :path "/foo"})))
    (is (not (allowed-by :get "/foob" {:method "get" :path "/foo"}))))
  (testing "method wildcard"
    (is (allowed-by :get "/foo" {:method "any" :path "/foo"}))
    (is (allowed-by :post "/foo" {:method "any" :path "/foo"}))
    (is (not (allowed-by :get "/foob" {:method "any" :path "/foo"}))))
  (testing "path regex"
    (is (allowed-by :get "/foo" {:method "get" :path "/f[^b]*"}))
    (is (not (allowed-by :put "/foo" {:method "get" :path "/f[^b]*"})))
    (is (allowed-by :get "/fizzle/pop" {:method "get" :path "/f[^b]*"}))
    (is (not (allowed-by :get "/fi/b" {:method "get" :path "/f[^b]*"})))))

(defn valid? [key userid method path]
  (when-let [key (get-api-key key)]
    (and (or (nil? (:users key))
             (some? (some #{userid} (:users key))))
         (or (nil? (:paths key))
             (some? (some (partial allowed-by method path) (:paths key)))))))

(defn add-api-key! [key & [{:keys [comment users paths]}]]
  (doseq [entry paths]
    (assert (= #{:method :path} (set (keys entry)))
            (str "Invalid path whitelist entry: " (pr-str entry)))
    (assert (contains? #{"get" "put" "post" "patch" "delete" "head" "options" "any"} (:method entry))
            (str "Invalid method: " (pr-str entry))))
  (db/upsert-api-key! {:apikey key
                       :comment comment
                       :users (when users (json/generate-string users))
                       :paths (when paths (json/generate-string paths))}))

(defn update-api-key! [key & [opts]]
  (add-api-key! key (merge (get-api-key key)
                           opts)))

(defn delete-api-key! [key]
  (db/delete-api-key! {:apikey key}))
