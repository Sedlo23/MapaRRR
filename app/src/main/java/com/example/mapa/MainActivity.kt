package com.example.mapa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream

data class Place(val location: GeoPoint, val photoPath: String, val text: String)
data class PlaceInfo(val place: Place, var photo: Bitmap?, var mapScreenshot: Bitmap?)


class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var mapView: MapView
    private lateinit var editText: EditText
    private lateinit var emailButton: Button
    private lateinit var placeRecyclerView: RecyclerView
    private val placeList = mutableListOf<PlaceInfo>()

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor

    private var currentLocation: GeoPoint? = null
    private var currentOrientation: Float = 0f

    private var magnetometerValues: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        mapView = findViewById(R.id.mapView)
        editText = findViewById(R.id.editText)
        emailButton = findViewById(R.id.emailButton)
        placeRecyclerView = findViewById(R.id.placeRecyclerView)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(0.0, 0.0))

        val prague = GeoPoint(50.0755, 14.4378)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(prague)

        emailButton.setOnClickListener {
            sharePhotos()
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener
            )
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!

        val savedPlaces = loadPlacesFromFile()

        placeRecyclerView.adapter = PlaceAdapter(placeList) { placeInfo ->

                val capturedImageFile = File(placeInfo.place.photoPath)
                val waypointImageFile = createTempImageFile(placeInfo.mapScreenshot!!, "map_screenshot")

                val capturedImageUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    capturedImageFile
                )
                val waypointImageUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    waypointImageFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_SUBJECT, "Shared Photos")
                    putExtra(Intent.EXTRA_TEXT, "Check out these photos!")
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM, arrayListOf(
                            capturedImageUri,
                            waypointImageUri
                        )
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooserIntent = Intent.createChooser(shareIntent, "Share photos using")
                startActivity(chooserIntent)
            }




        placeList.addAll(savedPlaces.map { PlaceInfo(it, null,null) })

        placeRecyclerView.layoutManager = LinearLayoutManager(this)


        requestPermissions()
    }



    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = GeoPoint(location.latitude, location.longitude)
            updateCameraPosition()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val accelerometerValues = event.values.clone()

            if (accelerometerValues != null && magnetometerValues != null) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magnetometerValues)

                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                currentOrientation = azimuthInDegrees
                updateCameraPosition()
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerValues = event.values.clone()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun updateCameraPosition() {
        if (currentLocation != null) {
            mapView.controller.animateTo(currentLocation)
            mapView.overlays.clear()

            val marker = Marker(mapView)
            marker.position = currentLocation
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(marker)

            val points = ArrayList<GeoPoint>()
            points.add(currentLocation!!)
            val directionPoint = calculateDirectionPoint(currentLocation, currentOrientation)
            points.add(directionPoint)
            val polyline = Polyline()
            polyline.setPoints(points)
            mapView.overlays.add(polyline)

            mapView.invalidate()
        }
    }

    private fun calculateDirectionPoint(location: GeoPoint?, orientation: Float): GeoPoint {
        val distance = 0.01 // Adjust the distance as needed
        val bearing = Math.toRadians(orientation.toDouble())
        val lat1 = Math.toRadians(location?.latitude ?: 0.0)
        val lon1 = Math.toRadians(location?.longitude ?: 0.0)
        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(distance / 6371.0) +
                    Math.cos(lat1) * Math.sin(distance / 6371.0) * Math.cos(bearing)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearing) * Math.sin(distance / 6371.0) * Math.cos(lat1),
            Math.cos(distance / 6371.0) - Math.sin(lat1) * Math.sin(lat2)
        )
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun generateWaypointImage(): Bitmap {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val mapSnapshot = takeMapSnapshot()
        canvas.drawBitmap(mapSnapshot, 0f, 0f, null)

        return bitmap
    }

    private fun takeMapSnapshot(): Bitmap {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        mapView.draw(canvas)

        return bitmap
    }

    private fun createTempImageFile(image: Bitmap, name: String): File {
        val file = File(cacheDir, "$name.jpg")
        file.createNewFile()

        val outputStream = FileOutputStream(file)
        image.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()

        return file
    }

    private fun sharePhotos() {
        // Take a photo
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                val capturedImageFile = createTempImageFile(imageBitmap, "2")
                val waypointImageFile = createTempImageFile(generateWaypointImage(), "1")

                val photoPath = capturedImageFile.absolutePath
                val place = Place(currentLocation!!, photoPath, editText.text.toString())
                val waypointImage = generateWaypointImage()
                placeList.add(PlaceInfo(place, imageBitmap, waypointImage))
                savePlacesToFile(placeList.map { it.place })

                placeList.last().photo = imageBitmap
                placeRecyclerView.adapter?.notifyDataSetChanged()

                val capturedImageUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    capturedImageFile
                )
                val waypointImageUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    waypointImageFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_SUBJECT, "Shared Photos")
                    putExtra(Intent.EXTRA_TEXT, "Check out these photos!")
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM, arrayListOf(
                            capturedImageUri,
                            waypointImageUri
                        )
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooserIntent = Intent.createChooser(shareIntent, "Share photos using")
                startActivity(chooserIntent)
            }
        }
    }

    private fun savePlacesToFile(places: List<Place>) {
        val json = Gson().toJson(places)
        val file = File(getExternalFilesDir(null), "places.json")
        file.writeText(json)
    }

    private fun loadPlacesFromFile(): List<Place> {
        val file = File(getExternalFilesDir(null), "places.json")
        if (file.exists()) {
            val json = file.readText()
            val type = object : TypeToken<List<Place>>() {}.type
            return Gson().fromJson(json, type)
        }
        return emptyList()
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        mapView.onPause()
    }
}