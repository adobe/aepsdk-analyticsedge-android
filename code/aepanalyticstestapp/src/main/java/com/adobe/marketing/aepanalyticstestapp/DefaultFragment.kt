package com.adobe.marketing.aepanalyticstestapp

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.MobilePrivacyStatus

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class DefaultFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.default_fragment_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setup button click listeners
        var sendTrackActionButton = view.findViewById<Button>(R.id.send_track_action)
        var sendTrackStateButton = view.findViewById<Button>(R.id.send_track_state)
        var setPrivacyOptedOutButton = view.findViewById<Button>(R.id.privacy_opt_out)
        var setPrivacyOptedInButton = view.findViewById<Button>(R.id.privacy_opt_in)

        val contextData = mutableMapOf<String, String>()
        contextData["key1"] = "value1"
        contextData["key2"] = "value2"

        sendTrackActionButton.setOnClickListener {
            MobileCore.trackAction("android test action", contextData)
        }

        sendTrackStateButton.setOnClickListener {
            MobileCore.trackState("android test state", contextData)
        }

        setPrivacyOptedOutButton.setOnClickListener {
            MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT)
        }

        setPrivacyOptedInButton.setOnClickListener {
            MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN)
        }
    }
}
