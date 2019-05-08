package com.leomedeiros.trackerconsumer

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.util.Log
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.DexterBuilder
import com.karumi.dexter.listener.PermissionRequest
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.provider.Settings
import br.com.phonetracker.lib.Interfaces.LocationServiceInterface
import br.com.phonetracker.lib.Services.LocationService
import br.com.phonetracker.lib.Services.ServicesHelper
import br.com.phonetracker.lib.utils.Logger
import br.com.phonetracker.lib.Tracker


class MainActivity : AppCompatActivity(), LocationServiceInterface {

    lateinit var tracker: Tracker
    lateinit var dexterPermissionBuilder: DexterBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dexterPermissionBuilder = Dexter.withActivity(this)
            .withPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, RECEIVE_BOOT_COMPLETED, WAKE_LOCK)
            .withListener(object : MultiplePermissionsListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionsChecked(report: MultiplePermissionsReport) =
                    if (report.areAllPermissionsGranted()) {
                        Logger.d("AllPermissionsGranted")

                        tracker = Tracker.Builder(this@MainActivity, resources.getXml(R.xml.aws_iot_settings))
                            .kalmanSettings(resources.getXml(R.xml.kalman_settings))
                            .restartIfKilled(true)
                            .build()

                        tracker.startTracking()

                        ServicesHelper.addLocationServiceInterface(this@MainActivity)

                    } else {
                        openSettings()
                    }

                override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>, token: PermissionToken) {
                    Log.d("MainActivity", "onPermissionRationaleShouldBeShown")
                    token.continuePermissionRequest()
                }
            })

        dexterPermissionBuilder.check()
    }

    // navigating user to app settings
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, 101)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            dexterPermissionBuilder.check()
        }
    }

    override fun locationChanged(location: Location?) {
        Logger.d("MainActivity locationChanged: $location")
    }


}
