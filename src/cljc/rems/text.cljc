(ns rems.text
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [clj-time.core :as time]
               :cljs [cljs-time.core :as time])
            #?(:clj [clj-time.format :as time-format]
               :cljs [cljs-time.format :as time-format])
            #?(:cljs [cljs-time.coerce :as time-coerce])
            [clojure.string :as str]
            [rems.common.application-util :as application-util]
            #?(:clj [rems.context :as context])
            [rems.tempura]))

#?(:clj
   (defmacro with-language [lang & body]
     `(binding [rems.context/*lang* ~lang]
        (assert (keyword? ~lang) {:lang ~lang})
        ~@body)))

(defn- failsafe-fallback
  "Fallback for when loading the translations has failed."
  [k args]
  (pr-str (vec (if (= :t/missing k)
                 (first args)
                 (cons k args)))))

(defn text-format
  "Return the tempura translation for a given key and arguments.
   Map can be used for named parameters as `(first args)`,
   when the localization resource supports them. `(rest args)`
   are then used as vector arguments if localization resource does not support
   named parameters. See `rems.tempura/tr`

   (text-format :key {:arg :value} varg-1 varg-2 ...)"
  [k & args]
  #?(:clj (rems.tempura/tr [k :t/missing]
                           args)
     :cljs (rems.tempura/tr [k :t/missing (failsafe-fallback k args)]
                            args)))

(defn text-no-fallback
  "Return the tempura translation for a given key. Additional fallback
  keys can be given but there is no default fallback text."
  [& ks]
  #?(:clj (rems.tempura/tr ks)
     :cljs (try
             (rems.tempura/tr ks)
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (apply text-no-fallback
                 (conj (vec ks)
                       (text-format :t/missing (vec ks))))
     ;; NB: we can't call the text-no-fallback here as in CLJS
     ;; we can both call this as function or use as a React component
     :cljs (try
             (rems.tempura/tr (conj (vec ks)
                                    (text-format :t/missing (vec ks))))
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn localized [m]
  (let [lang (rems.tempura/get-language)]
    (or (get m lang)
        (first (vals m)))))

;; TODO: replace usages of `get-localized-title` with `localized`
(defn get-localized-title [item language]
  (or (get-in item [:localizations language :title])
      (:title (first (vals (get item :localizations))))))

(def ^:private states
  {:application.state/draft :t.applications.states/draft
   :application.state/submitted :t.applications.states/submitted
   :application.state/approved :t.applications.states/approved
   :application.state/rejected :t.applications.states/rejected
   :application.state/closed :t.applications.states/closed
   :application.state/returned :t.applications.states/returned
   :application.state/revoked :t.applications.states/revoked})

(defn localize-state [state]
  (text (get states state :t.applications.states/unknown)))

(def ^:private todos
  {:new-application :t.applications.todos/new-application
   :no-pending-requests :t.applications.todos/no-pending-requests
   :resubmitted-application :t.applications.todos/resubmitted-application
   :waiting-for-decision :t.applications.todos/waiting-for-decision
   :waiting-for-review :t.applications.todos/waiting-for-review
   :waiting-for-your-decision :t.applications.todos/waiting-for-your-decision
   :waiting-for-your-review :t.applications.todos/waiting-for-your-review})

(defn localize-todo [todo]
  (if (nil? todo)
    ""
    (text (get todos todo :t.applications.todos/unknown))))

(defn- time-format []
  (time-format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn- time-format-with-seconds []
  (time-format/formatter "yyyy-MM-dd HH:mm:ss" (time/default-time-zone)))

(defn localize-time [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format) (time/to-default-time-zone time))))))

(defn localize-time-with-seconds
  "Localized datetime with second precision."
  [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format-with-seconds) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format-with-seconds) (time/to-default-time-zone time))))))

(defn localize-utc-date
  "For a given time instant, return the ISO date (yyyy-MM-dd) that it corresponds to in UTC."
  [time]
  #?(:clj (time-format/unparse (time-format/formatter "yyyy-MM-dd") time)
     :cljs (time-format/unparse (time-format/formatter "yyyy-MM-dd") (time-coerce/to-local-date time))))

(defn format-utc-datetime
  "For a given time instant, format it in UTC."
  [time]
  (time-format/unparse (time-format/formatters :date-time) time))

(deftest test-localize-utc-date []
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 1 1))))
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 23 59))))
  ;; [cl]js dates are always in UTC, so we can only test these for clj
  #?(:clj (do
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 23 59)
                                                                      (time/time-zone-for-offset 5)))))
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 1 1)
                                                                      (time/time-zone-for-offset -5))))))))

(def ^:private event-types
  {:application.event/applicant-changed :t.applications.events/applicant-changed
   :application.event/approved :t.applications.events/approved
   :application.event/attachments-redacted :t.applications.events/attachments-redacted
   :application.event/closed :t.applications.events/closed
   :application.event/review-requested :t.applications.events/review-requested
   :application.event/reviewed :t.applications.events/reviewed
   :application.event/copied-from :t.applications.events/copied-from
   :application.event/copied-to :t.applications.events/copied-to
   :application.event/created :t.applications.events/created
   :application.event/decided :t.applications.events/decided
   :application.event/decider-invited :t.applications.events/decider-invited
   :application.event/decider-joined :t.applications.events/decider-joined
   :application.event/decision-requested :t.applications.events/decision-requested
   :application.event/deleted :t.applications.events/deleted
   :application.event/draft-saved :t.applications.events/draft-saved
   :application.event/external-id-assigned :t.applications.events/external-id-assigned
   :application.event/expiration-notifications-sent :t.applications.events/expiration-notifications-sent
   :application.event/licenses-accepted :t.applications.events/licenses-accepted
   :application.event/licenses-added :t.applications.events/licenses-added
   :application.event/member-added :t.applications.events/member-added
   :application.event/member-invited :t.applications.events/member-invited
   :application.event/member-joined :t.applications.events/member-joined
   :application.event/member-removed :t.applications.events/member-removed
   :application.event/member-uninvited :t.applications.events/member-uninvited
   :application.event/rejected :t.applications.events/rejected
   :application.event/remarked :t.applications.events/remarked
   :application.event/resources-changed :t.applications.events/resources-changed
   :application.event/returned :t.applications.events/returned
   :application.event/revoked :t.applications.events/revoked
   :application.event/reviewer-invited :t.applications.events/reviewer-invited
   :application.event/reviewer-joined :t.applications.events/reviewer-joined
   :application.event/submitted :t.applications.events/submitted
   :application.event/voted :t.applications.events/voted})

(defn localize-user
  "Returns localization for special user if possible. Otherwise returns formatted user."
  [user]
  (case (:userid user)
    "rems-handler" (text :t.roles/anonymous-handler)
    (application-util/get-member-name user)))

(defn localize-decision [event]
  (when-let [decision (:application/decision event)]
    (text-format
     (case decision
       :approved :t.applications.events/approved
       :rejected :t.applications.events/rejected
       :t.applications.events/unknown)
     (localize-user (:event/actor-attributes event)))))

(defn localize-invitation [{:keys [name email]}]
  (str name " <" email ">"))

(defn localize-event [event]
  (let [event-type (:event/type event)]
    (str
     (text-format
      (get event-types event-type :t.applications.events/unknown)
      (localize-user (:event/actor-attributes event))
      (case event-type
        :application.event/review-requested
        (str/join ", " (mapv localize-user
                             (:application/reviewers event)))

        :application.event/decision-requested
        (str/join ", " (mapv localize-user
                             (:application/deciders event)))

        :application.event/created
        (:application/external-id event)

        :application.event/external-id-assigned
        (:application/external-id event)

        (:application.event/member-added
         :application.event/member-removed)
        (localize-user (:application/member event))

        :application.event/applicant-changed
        (localize-user (:application/applicant event))

        (:application.event/member-invited
         :application.event/member-uninvited)
        (localize-invitation (:application/member event))

        :application.event/decider-invited
        (localize-invitation (:application/decider event))

        :application.event/reviewer-invited
        (localize-invitation (:application/reviewer event))

        :application.event/resources-changed
        (str/join ", " (mapv #(localized (:catalogue-item/title %))
                             (:application/resources event)))

        :application.event/attachments-redacted
        (when (seq (:event/attachments event))
          (text :t.applications/redacted-attachments-replaced))

        :application.event/voted
        (when-not (str/blank? (:vote/value event))
          (text (keyword (str "t" ".applications.voting.votes") (:vote/value event))))

        nil))
     (case event-type
       :application.event/approved
       (when-let [end (:entitlement/end event)]
         (str " " (text-format :t.applications/entitlement-end (localize-utc-date end))))

       nil))))

(defn localize-attachment
  "If attachment is redacted, return localized text for redacted attachment.
   Otherwise return value of :attachment/filename."
  [attachment]
  (let [filename (:attachment/filename attachment)]
    (cond
      (= :filename/redacted filename)
      (text :t.applications/attachment-filename-redacted)

      (:attachment/redacted attachment)
      (text-format :t.label/parens filename (text :t.applications/attachment-filename-redacted))

      :else filename)))

(def ^:private localized-roles
  {;; :api-key
   :applicant :t.roles/applicant
   :decider :t.roles/decider
   ;; :everyone-else
   ;; :expirer
   :handler :t.roles/handler
   ;; :logged-in
   :member :t.roles/member
   ;; :organization-owner
   ;; :owner
   :past-decider :t.roles/past-decider
   :past-reviewer :t.roles/past-reviewer
   ;; :reporter
   :reviewer :t.roles/reviewer
   ;; :user-owner
   })

(defn localize-role [role]
  (text (get localized-roles role) :t/unknown))

(def ^:private localized-commands
  {:application.command/accept-invitation :t.commands/accept-invitation
   :application.command/accept-licenses :t.commands/accept-licenses
   :application.command/add-licenses :t.commands/add-licenses
   :application.command/add-member :t.commands/add-member
   :application.command/approve :t.commands/approve
   :application.command/assign-external-id :t.commands/assign-external-id
   :application.command/change-applicant :t.commands/change-applicant
   :application.command/change-resources :t.commands/change-resources
   :application.command/close :t.commands/close
   :application.command/copy-as-new :t.commands/copy-as-new
   ;; :application.command/create
   :application.command/decide :t.commands/decide
   :application.command/delete :t.commands/delete
   :application.command/invite-decider :t.commands/invite-decider
   :application.command/invite-member :t.commands/invite-member
   :application.command/invite-reviewer :t.commands/invite-reviewer
   :application.command/redact-attachments :t.commands/redact-attachments
   :application.command/reject :t.commands/reject
   :application.command/remark :t.commands/remark
   :application.command/remove-member :t.commands/remove-member
   :application.command/request-decision :t.commands/request-decision
   :application.command/request-review :t.commands/request-review
   :application.command/return :t.commands/return
   :application.command/review :t.commands/review
   :application.command/revoke :t.commands/revoke
   :application.command/save-draft :t.commands/save-draft
   ;; :application.command/send-expiration-notifications
   :application.command/submit :t.commands/submit
   :application.command/uninvite-member :t.commands/uninvite-member
   :application.command/vote :t.commands/vote})

(defn localize-command [command]
  (let [command-type (if (keyword? command)
                       command
                       (:type command))]
    (text (get localized-commands command-type) :t/unknown)))
