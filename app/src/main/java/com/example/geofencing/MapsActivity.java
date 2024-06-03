package com.example.geofencing;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private static final String TAG = "MapsActivity";

    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;

    private float GEOFENCE_RADIUS = 200;
    private String GEOFENCE_ID = "SOME_GEOFENCE_ID";

    private int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;
    private int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002;
    private static final int POLYGON_STROKE_WIDTH_PX = 5; // Largeur de la ligne du polygone en pixels
    private static final int POLYGON_FILL_COLOR = Color.argb(128, 255, 0, 0); // Couleur de remplissage du polygone (rouge avec une opacité de 50%)

    private List<LatLng> polygonPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        geofencingClient = LocationServices.getGeofencingClient(this);
        geofenceHelper = new GeofenceHelper(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng rabat = new LatLng(34.020882, -6.841650);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(rabat, 16));

        enableUserLocation();

        mMap.setOnMapLongClickListener(this);
    }

    private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FINE_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mMap.setMyLocationEnabled(true);
            } else {
                // Permission denied
            }
        }

        if (requestCode == BACKGROUND_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "You can add geofences...", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "Background location access is necessary for geofences to trigger...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // Add the point to the polygonPoints list
        polygonPoints.add(latLng);

        // Draw polygon and spraying points
        drawPolygon();
        calculateAndDrawSprayingPoints();
    }

    private void drawPolygon() {
        if (polygonPoints.size() < 3) {
            Toast.makeText(this, "A polygon needs at least 3 points.", Toast.LENGTH_SHORT).show();
            return;
        }

        PolygonOptions polygonOptions = new PolygonOptions()
                .addAll(polygonPoints)
                .strokeWidth(POLYGON_STROKE_WIDTH_PX)
                .strokeColor(Color.RED)
                .fillColor(POLYGON_FILL_COLOR);
        mMap.addPolygon(polygonOptions);
    }
    private void calculateAndDrawSprayingPoints() {
        if (polygonPoints.size() < 3) {
            return; // Au moins 3 points sont nécessaires pour former un polygone
        }

        // Supprimer les anciens marqueurs de pulvérisation
        mMap.clear();

        // Diamètre de pulvérisation
        double sprayingDiameter = 1.5; // Diamètre de pulvérisation en mètres
        double distanceBetweenPoints = 1.45; // Distance entre chaque point de pulvérisation en mètres

        // Calculer les points de pulvérisation pour chaque segment du polygone
        for (int i = 0; i < polygonPoints.size(); i++) {
            LatLng point = polygonPoints.get(i);
            LatLng nextPoint = polygonPoints.get((i + 1) % polygonPoints.size()); // Point suivant dans le polygone

            // Calculer la direction vectorielle entre les deux points
            double dx = nextPoint.longitude - point.longitude;
            double dy = nextPoint.latitude - point.latitude;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Calculer le nombre de points de pulvérisation nécessaires le long de ce segment
            int numPoints = (int) Math.ceil(length / distanceBetweenPoints);

            // Calculer et dessiner les points de pulvérisation le long de ce segment
            for (int j = 0; j < numPoints; j++) {
                double t = (double) j / (numPoints - 1);
                double sprayingLat = point.latitude + t * dy;
                double sprayingLng = point.longitude + t * dx;
                LatLng sprayingPoint = new LatLng(sprayingLat, sprayingLng);

                // Dessiner un marqueur pour le point de pulvérisation
                mMap.addMarker(new MarkerOptions().position(sprayingPoint).title("Spraying Point"));
            }
        }
    }

    private void handleMapLongClick(LatLng latLng) {
        mMap.clear();
        addMarker(latLng);
        addGeofence(latLng, GEOFENCE_RADIUS);
        addPolygon();
    }

    private void addPolygon() {
        if (polygonPoints.size() < 3) {
            Toast.makeText(this, "A polygon needs at least 3 points.", Toast.LENGTH_SHORT).show();
            return;
        }

        mMap.addPolygon(new PolygonOptions().addAll(polygonPoints));
    }

    private void addGeofence(LatLng latLng, float radius) {
        Geofence geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL | Geofence.GEOFENCE_TRANSITION_EXIT);
        GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofence);
        PendingIntent pendingIntent = geofenceHelper.getPendingIntent();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "onSuccess: Geofence Added...");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        String errorMessage = geofenceHelper.getErrorString(e);
                        Log.d(TAG, "onFailure: " + errorMessage);
                    }
                });
    }

    private void addMarker(LatLng latLng) {
        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        mMap.addMarker(markerOptions);
    }

    private void addPolygonPoint(LatLng latLng) {
        polygonPoints.add(latLng);
    }
}
