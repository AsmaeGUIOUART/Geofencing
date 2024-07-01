package com.example.geofencing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceBroadcastReceive";

    private List<LatLng> polygonPoints = new ArrayList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Error receiving geofence event: " + geofencingEvent.getErrorCode());
            return;
        }

        // Récupérer le type de transition géofencing (entrée, séjour, sortie)
        int transitionType = geofencingEvent.getGeofenceTransition();

        // Vérifier le type de transition
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                handleGeofenceEnter(context, geofencingEvent.getTriggeringGeofences());
                break;
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                // Logique pour le séjour dans la zone géofencée
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                // Logique pour la sortie de la zone géofencée
                break;
            default:
                Log.e(TAG, "Unknown transition type: " + transitionType);
                break;
        }
    }

    // Méthode pour gérer l'entrée dans la zone géofencée
    private void handleGeofenceEnter(Context context, List<Geofence> triggeringGeofences) {
        for (Geofence geofence : triggeringGeofences) {
            String requestId = geofence.getRequestId();
            // Vérifier si le point de déclenchement est à l'intérieur du polygone
            LatLng triggeringPoint = extractGeofencePoint(geofence);
            if (isPointInPolygon(triggeringPoint)) {
                // Le point de déclenchement est à l'intérieur du polygone
                // Ajoutez ici votre logique pour gérer l'entrée dans la zone géofencée
                // Par exemple, afficher une notification, enregistrer l'événement dans une base de données, etc.
                // Vous pouvez également utiliser le contexte fourni pour démarrer une activité ou un service
                NotificationHelper notificationHelper = new NotificationHelper(context);
                List<LatLng> sprayingPoints = calculateSprayingPoints(triggeringPoint, polygonPoints); // Calculate the spraying points
                notificationHelper.sendHighPriorityNotification("Entered geofence zone", requestId, MapsActivity.class, sprayingPoints);
            }
        }
    }

    private List<LatLng> calculateSprayingPoints(LatLng triggeringPoint, List<LatLng> polygonPoints) {
        List<LatLng> sprayingPoints = new ArrayList<>();

        // Calculate the spraying points based on the triggering point
        // For example, you can use the same logic as in your MapsActivity class
        double sprayingDiameter = 1.5; // Diamètre de pulvérisation en mètres
        double distanceBetweenPoints = 1.45; // Distance entre chaque point de pulvérisation en mètres

        // Calculate the points of spraying for each segment of the polygon
        for (int i = 0; i < polygonPoints.size(); i++) {
            LatLng point = polygonPoints.get(i);
            LatLng nextPoint = polygonPoints.get((i + 1) % polygonPoints.size()); // Point suivant dans le polygone

            // Calculate the direction vector between the two points
            double dx = nextPoint.longitude - point.longitude;
            double dy = nextPoint.latitude - point.latitude;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Calculate the number of spraying points needed along this segment
            int numPoints = (int) Math.ceil(length / distanceBetweenPoints);

            // Calculate and add the spraying points along this segment
            for (int j = 0; j < numPoints; j++) {
                double t = (double) j / (numPoints - 1);
                double sprayingLat = point.latitude + t * dy;
                double sprayingLng = point.longitude + t * dx;
                LatLng sprayingPoint = new LatLng(sprayingLat, sprayingLng);

                sprayingPoints.add(sprayingPoint);
            }
        }

        return sprayingPoints;
    }

    // Méthode pour extraire le point de déclenchement à partir d'un objet Geofence
    private LatLng extractGeofencePoint(Geofence geofence) {
        String[] split = geofence.getRequestId().split("_");
        double latitude = Double.parseDouble(split[0]);
        double longitude = Double.parseDouble(split[1]);
        return new LatLng(latitude, longitude);
    }

    // Méthode pour vérifier si un point est à l'intérieur du polygone
    private boolean isPointInPolygon(LatLng point) {
        int i, j;
        boolean inside = false;
        int nvert = polygonPoints.size();

        for (i = 0, j = nvert - 1; i < nvert; j = i++) {
            if (((polygonPoints.get(i).latitude <= point.latitude && point.latitude < polygonPoints.get(j).latitude) ||
                    (polygonPoints.get(j).latitude <= point.latitude && point.latitude < polygonPoints.get(i).latitude)) &&
                    (point.longitude < (polygonPoints.get(j).longitude - polygonPoints.get(i).longitude) *
                            (point.latitude - polygonPoints.get(i).latitude) /
                            (polygonPoints.get(j).latitude - polygonPoints.get(i).latitude) +
                            polygonPoints.get(i).longitude)) {
                inside =!inside;
            }
        }

        return inside;
    }
}

