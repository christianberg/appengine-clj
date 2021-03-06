(ns appengine.environment
  (:import (com.google.apphosting.api ApiProxy ApiProxy$Environment)
           (com.google.appengine.tools.development ApiProxyLocalFactory LocalServerEnvironment)))

(defn local-server-environment
  "Returns a local server environment."  
  [ & [directory]]
  (let [directory (or directory (System/getProperty "java.io.tmpdir"))]
    (proxy [LocalServerEnvironment] []
      (getAppDir [] (java.io.File. directory)))))

(defn login-aware-proxy
  "Returns a local api proxy environment."
  [request]
  (let [email (:email (:session request))]
    (proxy [ApiProxy$Environment] []
      (isLoggedIn [] (boolean email))
      (getAuthDomain [] "")
      (getRequestNamespace [] "")
      (getDefaultNamespace [] "")
      (getAttributes [] (java.util.HashMap.))
      (getEmail [] (or email ""))
      (isAdmin [] true)
      (getAppId [] "local"))))

(defmacro with-appengine
  "Macro to set the environment for the current thread."
  [proxy body]
  `(last
    (doall [(ApiProxy/setEnvironmentForCurrentThread ~proxy) ~body])))

(defn environment-decorator
  "Decorates the given application with local api proxy environment."
  [application]
  (fn [request]
    (with-appengine (login-aware-proxy request)
      (application request))))

(defn init-appengine
  "Initialize the App Engine services."  
  [& [directory]]     
  (ApiProxy/setDelegate
   (.create
    (ApiProxyLocalFactory.)
    (local-server-environment directory))))
