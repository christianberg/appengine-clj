(ns appengine.test.users
  (:require [appengine.users :as users])
  (:use clojure.test))

(deftest user-info-given-request
  (let [user-info-map {:user "instance of User" :user-service "instance of UserService"}
        ring-request {:appengine/user-info user-info-map}]
    (is (= (users/user-info ring-request) user-info-map))))

(deftest wrap-requiring-login
  (testing "redirects to login when user isn't logged in"
    (let [fake-user-service (proxy [com.google.appengine.api.users.UserService] []
                              (isUserLoggedIn [] false)
                              (createLoginURL [dest] (str "/login?then=" dest)))
          request {:uri "/requested_path"
                   :appengine/user-info {:user nil :user-service fake-user-service}}]
      (let [wrapped-app-with-url (users/wrap-requiring-login #(throw (Exception.)) "/the_path")]
        (is (= {:status 302 :headers {"Location" "/login?then=/the_path"}}
               (wrapped-app-with-url request))))
      (let [wrapped-app-with-no-url (users/wrap-requiring-login #(throw (Exception.)))]
        (is (= {:status 302 :headers {"Location" "/login?then=/requested_path"}}
               (wrapped-app-with-no-url request))))))
  (testing "allows request to pass when user is logged in"
    (let [fake-user-service (proxy [com.google.appengine.api.users.UserService] []
                              (isUserLoggedIn [] true))
          request {:appengine/user-info {:user "instance of User" :user-service fake-user-service}}
          dummy-response {:status 200 :body "Hello"}
          dummy-app (fn [request] dummy-response)]
      (let [wrapped-app-with-url (users/wrap-requiring-login dummy-app "/the_path")]
        (is (= dummy-response (wrapped-app-with-url request))))
      (let [wrapped-app-with-no-url (users/wrap-requiring-login dummy-app)]
        (is (= dummy-response (wrapped-app-with-no-url request)))))))

(deftest wrap-requiring-admin
  (testing "rejects request when user is logged in but not admin"
           (let [fake-user-service (proxy [com.google.appengine.api.users.UserService] []
                                     (isUserLoggedIn [] true)
                                     (isUserAdmin [] false))
                 request {:appengine/user-info {:user "instance of User"
                                                :user-service fake-user-service}}
                 wrapped-app (users/wrap-requiring-admin #(throw (Exception.)))]
             (is (= {:status 403 :body "Access denied. You must be logged in as admin user!"}
                    (wrapped-app request)))))
  (testing "allows request to pass when user is logged in as admin"
           (let [fake-user-service (proxy [com.google.appengine.api.users.UserService] []
                                     (isUserLoggedIn [] true)
                                     (isUserAdmin [] true))
                 request {:appengine/user-info {:user "instance of User" :user-service fake-user-service}}
                 dummy-response {:status 200 :body "Hello"}
                 dummy-app (fn [request] dummy-response)
                 wrapped-app (users/wrap-requiring-admin dummy-app)]
             (is (= dummy-response (wrapped-app request))))))