(ns rems.profile
  (:require [cljs-time.core :as time-core]
            [re-frame.core :as rf]
            [rems.atoms :refer [document-title info-field]]
            [rems.collapsible :as collapsible]
            [rems.common.roles :as roles]
            [rems.flash-message :as flash-message]
            [rems.fetcher :as fetcher]
            [rems.guide-util :refer [component-info example]]
            [rems.spinner :as spinner]
            [rems.text :refer [text localize-utc-date]]
            [rems.user :as user]
            [rems.util :refer [put! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::form)
    :dispatch [::user-settings]}))

(defn- fetch-user-settings-success [result]
  ;; select only the keys that can be edited on this page
  (rf/dispatch [::set-form (select-keys result [:notification-email])])
  ;; update user settings in SPA state after user has changed them
  (rf/dispatch [:loaded-user-settings result]))

(fetcher/reg-fetcher ::user-settings "/api/user-settings" {:on-success fetch-user-settings-success})

(rf/reg-sub
 ::form
 (fn [db] (::form db)))

(rf/reg-event-db
 ::set-form
 (fn [db [_ form]]
   (assoc db ::form form)))

(rf/reg-event-fx
 ::save
 (fn [{:keys [db]} _]
   (let [description [text :t.profile/save]]
     (put! "/api/user-settings"
           {:params (::form db)
            :handler (flash-message/default-success-handler :top description
                                                            #(rf/dispatch [::user-settings]))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::generate-api-key
 (fn [{:keys [db]} _]
   (let [description [text :t.profile/generate-api-key]]
     (post! "/api/user-settings/generate-ega-api-key"
            {:params (::form db)
             :handler (flash-message/default-success-handler :top description
                                                             #(rf/dispatch [::user-settings]))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::delete-api-key
 (fn [{:keys [db]} _]
   (let [description [text :t.profile/delete-api-key]]
     (post! "/api/user-settings/delete-ega-api-key"
            {:params (::form db)
             :handler (flash-message/default-success-handler :top description
                                                             #(rf/dispatch [::user-settings]))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))


(rf/reg-sub
 ::missing-email?
 (fn [db _]
   (let [user (:user (:identity db))]
     (and user
          (not (:email user))
          (not (:notification-email (:user-settings db)))))))

;;;; UI

(defn- missing-email-warning-dialog []
  [:div.alert.alert-warning
   (text :t.profile/warning-about-missing-email)])

(defn missing-email-warning []
  (when @(rf/subscribe [::missing-email?])
    [missing-email-warning-dialog]))

(defn profile-page []
  (let [identity @(rf/subscribe [:identity])
        form @(rf/subscribe [::form])
        config @(rf/subscribe [:rems.config/config])]
    [:<>
     [document-title (text :t.navigation/profile)]
     [flash-message/component :top]
     [collapsible/component
      {:title (text :t.profile/settings)
       :always (if @(rf/subscribe [::user-settings :fetching?])
                 [spinner/big]
                 [:form
                  {:on-submit (fn [event]
                                (.preventDefault event)
                                (rf/dispatch [::save]))}

                  [:div.form-group
                   (text :t.profile/idp-email) ": " (or (:email (:user identity))
                                                        [:span.text-muted (text :t.profile/no-email)])]

                  (let [id "notification-email"]
                    [:div.form-group
                     [:label {:for id} (text :t.profile/notification-email) ":"]
                     [:input.form-control
                      {:type "email"
                       :id id
                       :value (:notification-email form)
                       :on-change (fn [event]
                                    (let [value (.. event -target -value)]
                                      (rf/dispatch [::set-form (assoc form :notification-email value)])))}]])

                  [:button.btn.btn-primary
                   {:type "submit"}
                   (text :t.profile/save)]])}]

     [:div.mt-3
      [collapsible/component
       {:title (text :t.profile/your-details)
        :always [:<>
                 [user/username (:user identity)]
                 [user/attributes (:user identity) false]]}]]

     (when (and (:enable-ega config) (roles/has-roles? :handler))
       [:div.mt-3
        [collapsible/component
         {:title (text :t.profile/ega-full)
          :always (if @(rf/subscribe [::user-settings :fetching?])
                    [spinner/big]
                    [:<>
                     (text :t.profile/ega-intro)
                     (if-let [ega-api-key-expiration-date (get-in @(rf/subscribe [::user-settings])
                                                                  [:ega :api-key-expiration-date])]
                       [:<>
                        (if (time-core/before? (time-core/now) ega-api-key-expiration-date)
                          (text :t.profile/ega-api-key-valid)
                          (text :t.profile/ega-api-key-expired))
                        [info-field (text :t.profile/ega-api-key-expiration-date) (localize-utc-date ega-api-key-expiration-date) {:inline? true}]
                        [:form
                         {:on-submit (fn [event]
                                       (.preventDefault event)
                                       (rf/dispatch [::delete-api-key]))}

                         [:button.btn.btn-primary
                          {:type "submit"}
                          (text :t.profile/delete-api-key)]]]
                       [:<>
                        (text :t.profile/ega-api-key-none)
                        [:form
                         {:on-submit (fn [event]
                                       (.preventDefault event)
                                       (rf/dispatch [::generate-api-key]))}

                         [:button.btn.btn-primary
                          {:type "submit"}
                          (text :t.profile/generate-api-key)]]])])}]])]))

(defn guide []
  [:div
   (component-info missing-email-warning-dialog)

   (example "warning message"
            [missing-email-warning-dialog])])
