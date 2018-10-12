package com.app.directiongooglemaps

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.app.FragmentActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

import com.google.android.gms.common.api.Status
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment
import com.google.android.gms.location.places.ui.PlaceSelectionListener
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.tbruyelle.rxpermissions2.RxPermissions
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import java.util.HashMap

class MapsActivity : FragmentActivity(), OnMapReadyCallback, LocationListener {
    private var googleMap: GoogleMap? = null
    private val markerPoints = ArrayList<LatLng>()
    private var locationManager: LocationManager? = null
    private var markerSource: Marker? = null
    private var markerDestination: Marker? = null
    private var polylineOptions: PolylineOptions? = null
    private var polyline: Polyline? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Getting reference to SupportMapFragment of the activity_main
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        getCurrentLocationInformation()
        checkGPSOnOff()
        /*
         * The following code example shows setting an AutocompleteFilter on a PlaceAutocompleteFragment to
         * set a filter returning only results with a precise address.
         */
        //        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
        //                .setTypeFilter(AutocompleteFilter.TYPE_FILTER_ADDRESS)
        //                .build();
        //        autocompleteFragment.setFilter(typeFilter);
        //
        //        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
        //            @Override
        //            public void onPlaceSelected(Place place) {
        //                // TODO: Get info about the selected place.
        //                Log.e("TAG", "Place: " + place.getName());//get place details here
        //            }
        //
        //            @Override
        //            public void onError(Status status) {
        //                // TODO: Handle the error.
        //                Log.i("TAG", "An error occurred: " + status);
        //            }
        //        });
    }

    private fun checkGPSOnOff() {
        val  manager:LocationManager = getSystemService( Context.LOCATION_SERVICE ) as LocationManager
        if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            buildAlertMessageNoGps()
        }
    }

    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
        builder.setPositiveButton("Save") { dialog, whichButton ->
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        builder.setNegativeButton("Cancel") { dialog, whichButton ->
            dialog.cancel()
        }
        builder.show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        this.googleMap!!.setOnMapClickListener { latLng ->
//            createRouteByClickingTwoPoints(latLng)
            //                addStaticLocation(googleMap);
            //                setDestinationByClickingPosition(latLng);
            navigateTheUser(latLng)
        }
    }

    private fun navigateTheUser(latLng: LatLng) {
        val lat = latLng.latitude
        val lng = latLng.longitude
        val format = "geo:0,0?q=$lat,$lng( Location title)"
        val uri = Uri.parse(format)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    override fun onLocationChanged(location: Location) {
        //        setLocationCustomMarker(location);
        setCurrentSourceLocation(location)
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {

    }

    override fun onProviderEnabled(provider: String) {

    }

    override fun onProviderDisabled(provider: String) {

    }

    private fun setDestinationByClickingPosition(latLng: LatLng) {
        // Add new markerSource to the Google Map Android API V2
        if (markerPoints.size >= 2) {
            markerPoints[1] = latLng
        } else {
            markerPoints.add(latLng)
        }
        if (markerDestination != null) {
            markerDestination!!.remove()
        }
        markerDestination = this@MapsActivity.googleMap!!.addMarker(MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) //To add default markerSource icon
                .title("Destination"))
        val origin = markerPoints[0]
        val dest = markerPoints[1]
        if (polyline != null) {
            polyline!!.remove()
        }
        // Getting URL to the Google Directions API
        val url = getDirectionsUrl(origin, dest)
        val downloadTask = DownloadTask()
        // Start downloading json data from Google Directions API
        downloadTask.execute(url)
    }

    private fun createRouteByClickingTwoPoints(latLng: LatLng) {
        if (markerPoints.size > 1) {
            markerPoints.clear()
            googleMap!!.clear()
        }
        // Adding new item to the ArrayList
        markerPoints.add(latLng)
        // Creating MarkerOptions
        val options = MarkerOptions()
        // Setting the position of the marker
        options.position(latLng)
        /**
         * For the start location, the color of marker is GREEN and
         * for the end location, the color of marker is RED.
         */
        if (markerPoints.size == 1) {
            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        } else if (markerPoints.size == 2) {
            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        }
        // Add new marker to the Google Map Android API V2
        googleMap!!.addMarker(options)
        // Checks, whether start and end locations are captured
        if (markerPoints.size >= 2) {
            val origin = markerPoints[0]
            val dest = markerPoints[1]
            // Getting URL to the Google Directions API
            val url = getDirectionsUrl(origin, dest)
            val downloadTask = DownloadTask()
            // Start downloading json data from Google Directions API
            downloadTask.execute(url)
        }
    }

    private fun addStaticLocation(googleMap: GoogleMap) {
        //         TODO: 05-Mar-18 adds the lat and long statically according to your number of times
        val latLng = LatLng(30.731964, 76.7360608)
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15f)
        googleMap.animateCamera(cameraUpdate)
        googleMap.addMarker(MarkerOptions().position(latLng).title("You are here"))
    }

    private fun setCurrentSourceLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        // TODO: 05-Mar-18 used for zooming effect on googleMap upto max 21
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15f)
        googleMap!!.animateCamera(cameraUpdate)
        if (markerSource != null) {
            markerSource!!.remove()
        }
        markerSource = googleMap!!.addMarker(MarkerOptions()
                .position(latLng)
                .snippet("Lat:" + location.altitude + "Lng:" + location.longitude) // show the details when click on markerSource
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) //To add default markerSource icon
                .title("ME"))
        if (markerPoints.size >= 1) {
            markerPoints[0] = latLng
        } else {
            markerPoints.add(latLng)
        }
    }

    private fun setLocationCustomMarker(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        // TODO: 05-Mar-18 used for zooming effect on map upto max 21
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15f)
        googleMap!!.animateCamera(cameraUpdate)
        // TODO: 05-Mar-18 change the size of marker icon programmatically
        val height = 100
        val width = 100
        val bitmapdraw = resources.getDrawable(R.drawable.ic_launcher_background) as BitmapDrawable
        val b = bitmapdraw.bitmap
        val smallMarker = Bitmap.createScaledBitmap(b, width, height, false)
        // TODO: 05-Mar-18 used to set the updated marker on map by removing old one
        if (markerSource != null) {
            markerSource!!.remove()
        }
        markerSource = googleMap!!.addMarker(MarkerOptions()
                .position(latLng)
                .snippet("Lat:" + location.latitude + "Lng:" + location.longitude) // show the details when click on marker
                //                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) //To add default marker icon
                //                .icon(BitmapDescriptorFactory.fromResource(R.drawable.smallMarker)) // Set directly icon
                .icon(BitmapDescriptorFactory.fromBitmap(smallMarker))// if want to change the size of icon in android
                .title("ME"))
    }

    @SuppressLint("CheckResult")
    private fun getCurrentLocationInformation() {
        val rxPermissions = RxPermissions(this)
        rxPermissions
                .request(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION) // ask single or multiple permission once
                .subscribe { granted ->
                    if (granted!!) {
                        try {
                            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            if (locationManager != null) {
                                locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5f, this)
                            }
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    } else {
                    }
                }
        // TODO: 05-Mar-18 initialize the location to get the current information of location
    }

    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {
        // Origin of route
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude
        // Destination of route
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude
        // Sensor enabled
        val sensor = "sensor=false"
        // Building the parameters to the web service
        val parameters = "$str_origin&$str_dest&$sensor"
        // Output format
        val output = "json"
        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters"
    }

    /** A method to download json data from url  */
    @SuppressLint("LongLogTag")
    @Throws(IOException::class)
    private fun downloadUrl(strUrl: String): String {
        var data = ""
        var iStream: InputStream? = null
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(strUrl)

            // Creating an http connection to communicate with url
            urlConnection = url.openConnection() as HttpURLConnection

            // Connecting to url
            urlConnection.connect()

            // Reading data from url
            iStream = urlConnection.inputStream

            val br = BufferedReader(InputStreamReader(iStream!!))

            val sb = StringBuffer()

            var line = ""
//            while ((line = br.readLine()) != null) {
            while ((line == br.readLine()) != null) {
                sb.append(line)
            }

            data = sb.toString()

            br.close()

        } catch (e: Exception) {
            Log.d("Exception while downloading url", e.toString())
        } finally {
            iStream!!.close()
            urlConnection!!.disconnect()
        }
        return data
    }

    // Fetches data from url passed
    private inner class DownloadTask : AsyncTask<String, Void, String>() {
        // Downloading data in non-ui thread
        override fun doInBackground(vararg url: String): String {
            // For storing data from web service
            var data = ""
            try {
                // Fetching the data from web service
                data = downloadUrl(url[0])
            } catch (e: Exception) {
                Log.d("Background Task", e.toString())
            }

            return data
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            val parserTask = ParserTask()

            // Invokes the thread for parsing the JSON data
            parserTask.execute(result)
        }
    }

    /** A class to parse the Google Places in JSON format  */
    private inner class ParserTask : AsyncTask<String, Int, List<List<HashMap<String, String>>>>() {
        // Parsing the data in non-ui thread
        override fun doInBackground(vararg jsonData: String): List<List<HashMap<String, String>>>? {
            val jObject: JSONObject
            var routes: List<List<HashMap<String, String>>>? = null
            try {
                jObject = JSONObject(jsonData[0])
                val parser = DirectionsJSONParser()
                // Starts parsing data
                routes = parser.parse(jObject)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return routes
        }

        // Executes in UI thread, after the parsing process
        override fun onPostExecute(result: List<List<HashMap<String, String>>>) {
            var points: ArrayList<LatLng>? = null
            val markerOptions = MarkerOptions()

            // Traversing through all the routes
            for (i in result.indices) {
                points = ArrayList()
                polylineOptions = PolylineOptions()

                // Fetching i-th route
                val path = result[i]

                // Fetching all the points in i-th route
                for (j in path.indices) {
                    val point = path[j]

                    val lat = java.lang.Double.parseDouble(point["lat"])
                    val lng = java.lang.Double.parseDouble(point["lng"])
                    val position = LatLng(lat, lng)

                    points.add(position)
                }

                // Adding all the points in the route to LineOptions
                polylineOptions!!.addAll(points)
                polylineOptions!!.width(2f)
                polylineOptions!!.color(Color.RED)
            }

            // Drawing polyline in the Google Map for the i-th route
            //            googleMap.addPolyline(polylineOptions);
            polyline = googleMap!!.addPolyline(polylineOptions)
        }
    }

    private fun turnGPSOn() {
        val provider = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.LOCATION_PROVIDERS_ALLOWED)
        if (!provider.contains("gps")) {
            val poke = Intent()
            poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider")
            poke.addCategory(Intent.CATEGORY_ALTERNATIVE)
            poke.data = Uri.parse("3")
            sendBroadcast(poke)
        }
    }

}
