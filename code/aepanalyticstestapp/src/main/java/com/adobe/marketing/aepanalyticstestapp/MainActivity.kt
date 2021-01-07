package com.adobe.marketing.aepanalyticstestapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import com.adobe.marketing.mobile.*

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // register AEP SDK extensions
        MobileCore.setApplication(this.application)
        MobileCore.setLogLevel(LoggingMode.VERBOSE)

        Analytics.registerExtension()
        Identity.registerExtension()
        Edge.registerExtension()
        Assurance.registerExtension()

        MobileCore.start {
            MobileCore.configureWithAppID("3805cb8645dd/315760ade17b/launch-da9fe87710a7-development")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_assurance -> {
                Assurance.startSession("grifflab://default?adb_validation_sessionid=a90b7c7b-9770-4326-b225-b9d3d403d42b")
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
