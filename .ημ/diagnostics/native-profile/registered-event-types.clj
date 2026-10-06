;; Read only event type names and setting metadata, never any recorded values.
(let [initialized-before? (jdk.jfr.FlightRecorder/isInitialized)
      recorder (jdk.jfr.FlightRecorder/getFlightRecorder)]
  {:initialized-before? initialized-before?
   :registered-event-types
   (->> (.getEventTypes recorder)
        (map (fn [^jdk.jfr.EventType event-type]
               {:name (.getName event-type)
                :enabled-default
                (some (fn [^jdk.jfr.SettingDescriptor setting]
                        (when (= "enabled" (.getName setting))
                          (.getDefaultValue setting)))
                      (.getSettingDescriptors event-type))}))
        (sort-by :name)
        vec)})
