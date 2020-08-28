(ns rems.api.services.organizations
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [medley.core :refer [assoc-some find-first]]
            [rems.api.services.dependencies :as dependencies]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :organization-owner :handler} user-roles))]
    (for [org organizations]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :organization/last-modified
                :organization/modifier
                :organization/review-emails
                :organization/owners)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:organization/owners org))) owner)))

(defn- organization-filters [userid owner organizations]
  (->> organizations
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(defn get-organizations [& [{:keys [userid owner enabled archived]}]]
  (->> (organizations/get-organizations-raw)
       (organization-filters userid owner)
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))))

(defn get-organization-raw [org]
  (->> (organizations/get-organizations-raw)
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn get-organization [userid org]
  (->> (get-organizations {:userid userid})
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn add-organization! [userid org]
  (try
    (organizations/add-organization! userid org)
    {:success true
     :organization/id (:organization/id org)}
    (catch Exception ex
      {:success false}
      (if (and (.getCause ex)
               (str/includes? (.getMessage (.getCause ex))
                              "duplicate key value violates unique constraint"))
        {:success false
         :errors [{:type :t.actions.errors/duplicate-id}]}
        {:success false})))) ; unkown error

(defn edit-organization! [userid org]
  (organizations/update-organization! (:organization/id org)
                                      (fn [db-organization]
                                        (if (contains? (set (map :userid (:organization/owners db-organization))) userid)
                                          (merge db-organization org (select-keys [:organization/id :organization/owners] db-organization)) ; org owner can't update owners
                                          (merge db-organization org (select-keys [:organization/id] db-organization)))))
  {:success true
   :organization/id (:organization/id org)})

(defn set-organization-enabled! [{:organization/keys [id] :keys [enabled]}]
  (organizations/update-organization! id (fn [organization] (assoc organization :enabled enabled)))
  {:success true})

(defn set-organization-archived! [{:organization/keys [id] :keys [archived]}]
  (or (dependencies/change-archive-status-error archived  {:organization/id id})
      (do
        (organizations/update-organization! id (fn [organization] (assoc organization :archived archived)))
        {:success true})))

(defn get-available-owners [] (users/get-users))
