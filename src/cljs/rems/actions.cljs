(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.search :as search]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db
                ::todo-applications
                ::handled-applications)
    :dispatch-n [[::todo-applications]
                 [:rems.table/reset]]}))

(search/reg-fetcher ::todo-applications "/api/applications/todo")
(search/reg-fetcher ::handled-applications "/api/applications/handled")

;;;; UI

;; TODO not implemented
(defn- load-application-states-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#load-application-states-modal" :disabled true}
   (text :t.actions/load-application-states)])

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

;; TODO not implemented
(defn- show-publications-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-publications-modal" :disabled true}
   (text :t.actions/show-publications)])

;; TODO not implemented
(defn- show-throughput-times-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-throughput-times-modal" :disabled true}
   (text :t.actions/show-throughput-times)])

(defn- report-buttons []
  [:div.form-actions.inline
   [load-application-states-button]
   [export-entitlements-button]
   [show-publications-button]
   [show-throughput-times-button]])

(defn application-list-defaults []
  (let [config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    {:visible-columns #{id-column :description :resource :applicant :state :submitted :last-activity :view}
     :default-sort-column :last-activity
     :default-sort-order :desc
     :filterable? false}))

;; TODO: deduplicate with rems.applications
(defn- todo-applications []
  (let [applications ::todo-applications]
    (cond
      (not @(rf/subscribe [applications :initialized?]))
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/empty)]

      :else
      [application-list/component
       (-> (application-list-defaults)
           (assoc :id applications
                  :applications applications))])))

(defn- handled-applications []
  (let [applications ::handled-applications]
    (cond
      (not @(rf/subscribe [applications :initialized?]))
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]

      :else
      [application-list/component
       (-> (application-list-defaults)
           (update :visible-columns disj :submitted)
           (assoc :id applications
                  :applications applications))])))

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   [:div.spaced-sections
    [collapsible/component
     {:id "todo-applications"
      :open? true
      :title (text :t.actions/todo-applications)
      :collapse [:<>
                 [search/search-field {:id "todo-search"
                                       :on-search #(rf/dispatch [::todo-applications %])
                                       :searching? @(rf/subscribe [::todo-applications :searching?])}]
                 [todo-applications]]}]
    [collapsible/component
     {:id "handled-applications"
      :on-open #(rf/dispatch [::handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [search/search-field {:id "handled-search"
                                       :on-search #(rf/dispatch [::handled-applications %])
                                       :searching? @(rf/subscribe [::handled-applications :searching?])}]
                 [handled-applications]]}]]])
