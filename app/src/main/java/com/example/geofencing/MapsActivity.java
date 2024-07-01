package com.example.geofencing;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private static final String TAG = "MapsActivity";

    private GoogleMap mMap;


    private static final int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;
    private static final int POLYGON_STROKE_WIDTH_PX = 5; // Largeur de la ligne du polygone en pixels
    private static final int POLYGON_FILL_COLOR = Color.argb(128, 255, 0, 0); // Couleur de remplissage du polygone (rouge avec une opacité de 50%)

    private List<LatLng> polygonPoints = new ArrayList<>();

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        prefs = getSharedPreferences("geofence_prefs", MODE_PRIVATE);

        // Load polygon points from SharedPreferences
        String polygonPointsJson = prefs.getString("polygon_points", null);
        if (polygonPointsJson!= null) {
            Gson gson = new Gson();
            polygonPoints = gson.fromJson(polygonPointsJson, new TypeToken<List<LatLng>>(){}.getType());
        }

        // Initialiser la carte
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Position par défaut de la caméra
        LatLng rabat = new LatLng(34.020882, -6.841650);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(rabat, 16));

        // Activer la localisation de l'utilisateur
        enableUserLocation();

        // Écouter les longs clics sur la carte
        mMap.setOnMapLongClickListener(this);
    }

    // Activer la localisation de l'utilisateur
    private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException: " + e.getMessage());
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FINE_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    mMap.setMyLocationEnabled(true);
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // Ajouter le point à la liste des points du polygone
        polygonPoints.add(latLng);

        // Effacer la carte avant de redessiner le polygone et les points de pulvérisation
        mMap.clear();

        // Dessiner le polygone
        drawPolygon();

        // Calculer et dessiner les points de pulvérisation
        calculateAndDrawSprayingPoints();

        // Store the polygon points in SharedPreferences
        Gson gson = new Gson();
        String polygonPointsJson = gson.toJson(polygonPoints);
        prefs.edit().putString("polygon_points", polygonPointsJson).apply();
    }

    // Dessiner le polygone sur la carte
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

    // Calculer et dessiner les points de pulvérisation
    private void calculateAndDrawSprayingPoints() {
        if (polygonPoints.size() < 3) {
            return; // Au moins 3 points sont nécessaires pour former un polygone
        }

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

                // Log pour débogage
                Log.d(TAG, "Spraying Point: " + sprayingPoint.latitude + ", " + sprayingPoint.longitude);
            }
        }
    }


}
