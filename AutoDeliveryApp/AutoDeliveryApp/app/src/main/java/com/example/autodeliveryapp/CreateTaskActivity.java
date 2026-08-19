package com.example.autodeliveryapp;

import com.example.autodeliveryapp.utils.PhoneUtils;
import com.example.autodeliveryapp.utils.NotificationUtils;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.example.autodeliveryapp.utils.LocationSuggestionAdapter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autodeliveryapp.databinding.ActivityCreateTaskBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.DatabaseReference;


import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CreateTaskActivity extends AppCompatActivity {
    


    private ActivityCreateTaskBinding binding;
    private double simulatedDistance = 0;
    private final Executor networkExecutor = Executors.newSingleThreadExecutor();

    // Debounce to prevent OSRM API spam when typing address
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debounceRunnable;



    private Style mapStyle = null;
    private String routeGeoJson = null;
    private MapLibreMap cachedMap = null;

    private String currentBleToken = null;

    // Route metadata stored for Firebase save
    private int currentDeliveryDuration = 0;
    private String currentRouteGeoJson = null;
    private String currentRouteSource = "";
    private String currentRouteStatus = "";

    private LatLng originLatLng;
    private LatLng destinationLatLng;
    
    private LatLng homeLatLng = null; // AGV Home Location

    // Selected slot ID
    private String selectedSlotId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);


        binding.mapView.onCreate(savedInstanceState);


        EdgeToEdgeHelper.applyStatusBarPadding(binding.btnBackWrapper);
        // Apply padding to both the bottom sheet and the confirm button container.
        // This makes the button slide up with the keyboard, while the bottom sheet
        // increases in height and expands upwards to cover the map, keeping inputs visible.
        EdgeToEdgeHelper.applyNavigationBarPadding(binding.bottomSheet);
        EdgeToEdgeHelper.applyNavigationBarPadding(binding.bottomActionArea);

        binding.btnBackCreateTask.setOnClickListener(v -> finish());


        binding.mapView.getMapAsync(map -> {
            cachedMap = map;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(getInitialMapCenter(), 12.0f));
            
            map.addOnMapLongClickListener(point -> {
                CharSequence[] options = new CharSequence[]{"Set as Pickup", "Set as Dropoff"};
                new android.app.AlertDialog.Builder(CreateTaskActivity.this)
                        .setTitle("Set Location")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                originLatLng = point;
                                binding.etPickup.setText("Loading...");
                                reverseGeocode(point, address -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    binding.etPickup.setText(address);
                                    binding.etPickup.dismissDropDown();
                                    triggerRouteCalculation();
                                });
                            } else if (which == 1) {
                                destinationLatLng = point;
                                binding.etDropoff.setText("Loading...");
                                reverseGeocode(point, address -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    binding.etDropoff.setText(address);
                                    binding.etDropoff.dismissDropDown();
                                    triggerRouteCalculation();
                                });
                            }
                        })
                        .show();
                return true;
            });

            map.setStyle(Constants.MAP_STYLE_URL, style -> {
                // [LIFECYCLE GUARD]
                if (binding == null || isFinishing() || isDestroyed()) return;
                mapStyle = style;
                android.util.Log.d("CreateTaskMap", "Map style loaded");
                enableLocationComponent(style);
                drawRouteIfReady();
                drawHomeMarker();
            });
        });
        
        // Fetch Home Location from Firebase
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("system/agv_home")
            .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    Double fbLat = snapshot.child("lat").getValue(Double.class);
                    Double fbLng = snapshot.child("lng").getValue(Double.class);
                    if (fbLat != null && fbLng != null && fbLat != 0 && fbLng != 0) {
                        homeLatLng = new LatLng(fbLat, fbLng);
                        if (mapStyle != null) {
                            runOnUiThread(() -> drawHomeMarker());
                        }
                    }
                }
                @Override
                public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
            });

        prefillUserInfo();
        setupAddressWatcher();

        LocationSuggestionAdapter pickupAdapter = new LocationSuggestionAdapter(this, R.layout.item_location_suggestion);
        binding.etPickup.setAdapter(pickupAdapter);

        LocationSuggestionAdapter dropoffAdapter = new LocationSuggestionAdapter(this, R.layout.item_location_suggestion);
        binding.etDropoff.setAdapter(dropoffAdapter);

        // Expand BottomSheet when typing to prevent keyboard from obscuring inputs
        com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.LinearLayout> bottomSheetBehavior = 
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(binding.bottomSheet);
        
        android.view.View.OnFocusChangeListener expandBottomSheet = (v, hasFocus) -> {
            if (hasFocus && bottomSheetBehavior.getState() != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        };

        binding.etPickup.setOnFocusChangeListener(expandBottomSheet);
        binding.etDropoff.setOnFocusChangeListener(expandBottomSheet);
        binding.etSenderName.setOnFocusChangeListener(expandBottomSheet);
        binding.etSenderPhone.setOnFocusChangeListener(expandBottomSheet);
        binding.etReceiverName.setOnFocusChangeListener(expandBottomSheet);
        binding.etReceiverPhone.setOnFocusChangeListener(expandBottomSheet);

        binding.etPickup.setOnItemClickListener((parent, view, position, id) -> {
            String selected = pickupAdapter.getItem(position);
            originLatLng = pickupAdapter.getCoordinates(selected);
            triggerRouteCalculation();
        });

        binding.etDropoff.setOnItemClickListener((parent, view, position, id) -> {
            String selected = dropoffAdapter.getItem(position);
            destinationLatLng = dropoffAdapter.getCoordinates(selected);
            triggerRouteCalculation();
        });

        binding.btnMyLocation.setOnClickListener(v -> {
            requestCurrentLocation();
        });

        binding.btnConfirm.setOnClickListener(v -> confirmTask());

        binding.btnZoomIn.setOnClickListener(v -> {
            if (cachedMap != null) {
                cachedMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        binding.btnZoomOut.setOnClickListener(v -> {
            if (cachedMap != null) {
                cachedMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });
    }



    public static String removeAccents(String src) {
        if (src == null) return "";
        String normalized = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase()
                .replaceAll("đ", "d")
                .replaceAll("Đ", "d");
    }

    public static String normalizeAddress(String address) {
        if (address == null) return "";
        String clean = removeAccents(address).trim();
        clean = clean.replaceAll("^(?:k|h|kiet|hem|so|ngo)?\\s*\\d+(?:/\\d+)*[a-z]?\\s*(?:duong)?\\s*", "");
        clean = clean.replaceAll(",\\s*(?:da nang|dn).*$", "");
        return clean.trim();
    }
    private interface OnAddressResolvedListener {
        void onAddressResolved(String address);
    }

    private void reverseGeocode(LatLng point, OnAddressResolvedListener listener) {
        networkExecutor.execute(() -> {
            try {
                String urlStr = "https://api.maptiler.com/geocoding/" + point.getLongitude() + "," + point.getLatitude() + ".json?key=" + Constants.MAPTILER_API_KEY;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray features = root.getJSONArray("features");
                    if (features.length() > 0) {
                        String placeName = features.getJSONObject(0).getString("place_name");
                        runOnUiThread(() -> listener.onAddressResolved(placeName));
                    } else {
                        runOnUiThread(() -> listener.onAddressResolved("Selected Location"));
                    }
                } else {
                    runOnUiThread(() -> listener.onAddressResolved("Selected Location"));
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> listener.onAddressResolved("Selected Location"));
            }
        });
    }



    /**
     * Fetches route geometry and distance from OSRM.
     * Falls back to Haversine distance when OSRM is unavailable.
     */
    private void fetchOsrmRoute(LatLng origin, LatLng destination) {
        // Show ProgressBar when calling API starts
        runOnUiThread(() -> {
            if (binding != null) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
        });

        networkExecutor.execute(() -> {


            String urlStr = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",

                    origin.getLongitude(), origin.getLatitude(),
                    destination.getLongitude(), destination.getLatitude());

            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "AutoDeliveryApp/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {

                    android.util.Log.w("OSRM", "HTTP error: " + responseCode + " - switching to Haversine fallback");
                    conn.disconnect();
                    handleOsrmFallback(origin, destination);
                    return;
                }


                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();


                JSONObject root = new JSONObject(sb.toString());
                JSONArray routes = root.getJSONArray("routes");
                JSONObject route0 = routes.getJSONObject(0);

                double distanceM = route0.getDouble("distance");
                double distanceKm = Math.round(distanceM / 100.0) / 10.0;



                JSONObject geometry = route0.getJSONObject("geometry");
                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");
                feature.put("geometry", geometry);
                feature.put("properties", new JSONObject());

                final String geoJson = feature.toString();

                // Compute ETA: distance × 5 min/km (rough estimate)
                final int etaMinutes = (int) Math.ceil(distanceKm * 5);
                final String geoJsonFinal = geoJson;

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) return;
                    binding.progressBar.setVisibility(View.GONE);
                    simulatedDistance = distanceKm;
                    binding.tvDistance.setText(
                            getString(R.string.format_distance_km, String.valueOf(simulatedDistance)));

                    binding.btnConfirm.setEnabled(simulatedDistance > 0);

                    // Store derived values for Firebase save
                    currentDeliveryDuration = etaMinutes;
                    currentRouteGeoJson = geoJsonFinal;
                    currentRouteSource = "osrm";
                    currentRouteStatus = "ok";

                    routeGeoJson = geoJsonFinal;
                    drawRouteIfReady();
                });

            } catch (Exception e) {
                android.util.Log.e("OSRM", "fetchOsrmRoute failed: " + e.getClass().getSimpleName());
                handleOsrmFallback(origin, destination);
            }
        });
    }

    /**
     * Handles OSRM failure by calculating an estimated distance with Haversine
     * and drawing a straight-line fallback route.
     */
    private void handleOsrmFallback(LatLng origin, LatLng destination) {
        double haversineKm = haversineKm(origin, destination);
        final int etaMinutes = (int) Math.ceil(haversineKm * 5);

        String fallbackGeoJson = buildStraightLineGeoJson(origin, destination);

        runOnUiThread(() -> {
            if (binding == null || isFinishing() || isDestroyed()) return;
            binding.progressBar.setVisibility(View.GONE);

            Toast.makeText(this, "Failed to get detailed route, using estimated distance.",
                    Toast.LENGTH_SHORT).show();

            simulatedDistance = haversineKm;
            binding.tvDistance.setText(
                    getString(R.string.format_distance_km, String.valueOf(simulatedDistance)));

            binding.btnConfirm.setEnabled(simulatedDistance > 0);

            currentDeliveryDuration = etaMinutes;
            currentRouteGeoJson = fallbackGeoJson;
            currentRouteSource = "haversine";
            currentRouteStatus = "fallback";

            routeGeoJson = fallbackGeoJson;
            drawRouteIfReady();
        });
    }



    /**
     * Draws the route only after both MapLibre style and route GeoJSON are ready.
     * This avoids race conditions between map loading and route fetching.
     */
    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(@androidx.annotation.NonNull org.maplibre.android.maps.Style loadedMapStyle) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            org.maplibre.android.location.LocationComponent locationComponent = cachedMap.getLocationComponent();
            locationComponent.activateLocationComponent(
                    org.maplibre.android.location.LocationComponentActivationOptions.builder(this, loadedMapStyle).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setRenderMode(org.maplibre.android.location.modes.RenderMode.COMPASS);
        }
    }

    private void drawRouteIfReady() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        if (mapStyle == null || routeGeoJson == null || cachedMap == null) {
            android.util.Log.d("CreateTaskMap", "drawRouteIfReady: waiting - mapStyle="
                    + (mapStyle != null) + ", routeGeoJson=" + (routeGeoJson != null));
            return;
        }
        if (originLatLng == null || destinationLatLng == null) return;

        try {
            final String SOURCE_ROUTE  = "ct-route-source";
            final String LAYER_ROUTE   = "ct-route-layer";
            final String SOURCE_MARKER_A = "ct-marker-a-source";
            final String LAYER_MARKER_A  = "ct-marker-a-layer";
            final String SOURCE_MARKER_B = "ct-marker-b-source";
            final String LAYER_MARKER_B  = "ct-marker-b-layer";

            // Remove previous layers/sources
            if (mapStyle.getLayer(LAYER_ROUTE) != null)    mapStyle.removeLayer(LAYER_ROUTE);
            if (mapStyle.getLayer(LAYER_MARKER_A) != null) mapStyle.removeLayer(LAYER_MARKER_A);
            if (mapStyle.getLayer(LAYER_MARKER_B) != null) mapStyle.removeLayer(LAYER_MARKER_B);
            if (mapStyle.getSource(SOURCE_ROUTE) != null)    mapStyle.removeSource(SOURCE_ROUTE);
            if (mapStyle.getSource(SOURCE_MARKER_A) != null) mapStyle.removeSource(SOURCE_MARKER_A);
            if (mapStyle.getSource(SOURCE_MARKER_B) != null) mapStyle.removeSource(SOURCE_MARKER_B);

            // Route polyline
            mapStyle.addSource(new GeoJsonSource(SOURCE_ROUTE, routeGeoJson));
            mapStyle.addLayer(new LineLayer(LAYER_ROUTE, SOURCE_ROUTE)
                    .withProperties(
                            PropertyFactory.lineColor("#10B981"),
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineOpacity(0.85f)));

            // Pickup Marker A — green circle
            JSONObject markerA = buildPointFeatureCollection(originLatLng);
            mapStyle.addSource(new GeoJsonSource(SOURCE_MARKER_A, markerA.toString()));
            mapStyle.addLayer(new CircleLayer(LAYER_MARKER_A, SOURCE_MARKER_A)
                    .withProperties(
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleColor("#3B82F6"),   // blue
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF")));

            // Delivery Marker B — red circle
            JSONObject markerB = buildPointFeatureCollection(destinationLatLng);
            mapStyle.addSource(new GeoJsonSource(SOURCE_MARKER_B, markerB.toString()));
            mapStyle.addLayer(new CircleLayer(LAYER_MARKER_B, SOURCE_MARKER_B)
                    .withProperties(
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleColor("#EF4444"),   // red
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF")));

            // Camera bounds
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder()
                    .include(originLatLng)
                    .include(destinationLatLng);
            if (homeLatLng != null) boundsBuilder.include(homeLatLng);
            final LatLngBounds bounds = boundsBuilder.build();
            
            binding.mapView.post(() -> {
                if (binding == null || isFinishing() || isDestroyed() || cachedMap == null) return;
                try {
                    cachedMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
                } catch (Exception e) {
                    cachedMap.moveCamera(CameraUpdateFactory.newLatLngZoom(originLatLng, 13));
                }
            });

            android.util.Log.d("CreateTaskMap", "Route drawn successfully");

        } catch (Exception e) {
            android.util.Log.e("CreateTaskMap", "drawRouteIfReady error: " + e.getMessage());
        }
    }
    
    private void drawHomeMarker() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        if (mapStyle == null || cachedMap == null || homeLatLng == null) return;
        
        try {
            if (mapStyle.getImage("home-icon") == null) {
                android.graphics.Bitmap homeIcon = getBitmapFromVectorDrawable(this, R.drawable.ic_nav_home);
                if (homeIcon != null) {
                    mapStyle.addImage("home-icon", homeIcon);
                }
            }

            if (mapStyle.getSource("source-home") == null) {
                mapStyle.addSource(new org.maplibre.android.style.sources.GeoJsonSource("source-home", buildPointFeatureCollection(homeLatLng).toString()));
                mapStyle.addLayer(new org.maplibre.android.style.layers.SymbolLayer("layer-home", "source-home")
                        .withProperties(
                                org.maplibre.android.style.layers.PropertyFactory.iconImage("home-icon"),
                                org.maplibre.android.style.layers.PropertyFactory.iconSize(1.5f),
                                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true)
                        ));
                
                // Pan camera to home if no origin is set
                if (originLatLng == null) {
                    cachedMap.animateCamera(CameraUpdateFactory.newLatLngZoom(homeLatLng, 15f));
                }
            } else {
                ((org.maplibre.android.style.sources.GeoJsonSource) mapStyle.getSource("source-home")).setGeoJson(buildPointFeatureCollection(homeLatLng).toString());
            }
        } catch (Exception e) {
            android.util.Log.e("CreateTaskMap", "drawHomeMarker error: " + e.getMessage());
        }
    }

    /** Build a GeoJSON FeatureCollection with a single Point feature. */
    private JSONObject buildPointFeatureCollection(LatLng point) throws Exception {
        JSONObject collection = new JSONObject();
        collection.put("type", "FeatureCollection");
        JSONArray features = new JSONArray();

        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        JSONObject geom = new JSONObject();
        geom.put("type", "Point");
        JSONArray coords = new JSONArray();
        coords.put(point.getLongitude());
        coords.put(point.getLatitude());
        geom.put("coordinates", coords);
        feature.put("geometry", geom);
        feature.put("properties", new JSONObject());
        features.put(feature);

        collection.put("features", features);
        return collection;
    }
    
    private android.graphics.Bitmap getBitmapFromVectorDrawable(android.content.Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        
        // Ensure size is valid, if intrinsic size is <= 0 use a fallback size
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 64;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 64;
        
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width,
                height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Apply tint if needed, ic_nav_home is likely a white/grey icon, let's tint it blue to match the original circle
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.parseColor("#3B82F6"));
        
        drawable.draw(canvas);
        return bitmap;
    }

    
    /**
     * Calculates straight-line distance in kilometers using the Haversine formula.
     */
    private double haversineKm(LatLng a, LatLng b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.getLatitude()  - a.getLatitude());
        double dLng = Math.toRadians(b.getLongitude() - a.getLongitude());
        double sinDlat = Math.sin(dLat / 2);
        double sinDlng = Math.sin(dLng / 2);
        double h = sinDlat * sinDlat
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * sinDlng * sinDlng;
        double dist = 2 * R * Math.asin(Math.sqrt(h));

        return Math.round(dist * 10.0) / 10.0;
    }

    
    /**
     * Builds a two-point GeoJSON LineString used when OSRM routing fails.
     */
    private String buildStraightLineGeoJson(LatLng origin, LatLng destination) {
        try {
            JSONObject feature = new JSONObject();
            feature.put("type", "Feature");

            JSONObject geometry = new JSONObject();
            geometry.put("type", "LineString");

            JSONArray coords = new JSONArray();


            JSONArray originCoord = new JSONArray();
            originCoord.put(origin.getLongitude());
            originCoord.put(origin.getLatitude());  // lat second

            JSONArray destCoord = new JSONArray();
            destCoord.put(destination.getLongitude());
            destCoord.put(destination.getLatitude());  // lat second

            coords.put(originCoord);
            coords.put(destCoord);
            geometry.put("coordinates", coords);

            feature.put("geometry", geometry);
            feature.put("properties", new JSONObject());
            return feature.toString();
        } catch (Exception e) {
            android.util.Log.e("CreateTaskMap", "buildStraightLineGeoJson error: " + e.getMessage());
            return null;
        }
    }



    private void prefillUserInfo() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("users")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (binding == null || isFinishing() || isDestroyed()) return;
                        String name  = snapshot.child("username").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        if (name  != null) binding.etSenderName.setText(name);
                        if (phone != null) binding.etSenderPhone.setText(phone);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.e("CreateTaskDB", "prefillUserInfo cancelled - " + error.getMessage());
                    }
                });
    }


    private LatLng getInitialMapCenter() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
                android.location.Location location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
                if (location != null) {
                    return new LatLng(location.getLatitude(), location.getLongitude());
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return new LatLng(16.047079, 108.206230); // Default Da Nang
    }

    @SuppressWarnings("MissingPermission")
    private void requestCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
            return;
        }

        try {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    originLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    
                    binding.etPickup.setText("Current Location", false);
                    binding.etPickup.dismissDropDown();
                    triggerRouteCalculation();
                    if (cachedMap != null) {
                        cachedMap.moveCamera(CameraUpdateFactory.newLatLngZoom(originLatLng, 15));
                    }
                } else {
                    Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(this, e -> {
                android.util.Log.e("Location", "Error getting location", e);
                Toast.makeText(this, "Error getting location", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            android.util.Log.e("Location", "Error getting location", e);
            Toast.makeText(this, "Error initializing location service", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestCurrentLocation();
        }
    }

    private void triggerRouteCalculation() {
        String pickup  = binding.etPickup.getText().toString().trim();
        String dropoff = binding.etDropoff.getText().toString().trim();
        
        if (!pickup.isEmpty() && !dropoff.isEmpty() && originLatLng != null && destinationLatLng != null) {
            binding.tvDistance.setText("Calculating...");
            binding.btnConfirm.setEnabled(false);
            routeGeoJson = null;
            fetchOsrmRoute(originLatLng, destinationLatLng);
        }
    }

    private void setupAddressWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (binding == null) return;
                
                String pickup = binding.etPickup.getText().toString().trim();
                String dropoff = binding.etDropoff.getText().toString().trim();
                
                // Only disable confirm if text is cleared or manually edited without picking from dropdown
                if (pickup.isEmpty() || dropoff.isEmpty()) {
                    binding.tvDistance.setText(R.string.placeholder_distance);
                    binding.btnConfirm.setEnabled(false);
                    simulatedDistance = 0;
                }
            }
        };
        binding.etPickup.addTextChangedListener(watcher);
        binding.etDropoff.addTextChangedListener(watcher);
    }





    
    /**
     * Enables or disables the form while Firebase write operations are running.
     */
    private void freezeForm(boolean freeze) {
        if (binding == null) return;
        boolean enable = !freeze;
        binding.btnConfirm.setEnabled(enable);
        binding.etPickup.setEnabled(enable);
        binding.etDropoff.setEnabled(enable);
        binding.etSenderName.setEnabled(enable);
        binding.etSenderPhone.setEnabled(enable);
        binding.etReceiverName.setEnabled(enable);
        binding.etReceiverPhone.setEnabled(enable);
    }

    /**
     * Validates task input, reserves a robot slot, writes task data to Firebase,
     * and starts receiver lookup.
     */
    private void confirmTask() {
        if (binding == null) return;

        String senderName  = binding.etSenderName.getText() != null
                ? binding.etSenderName.getText().toString().trim() : "";
        String senderPhone = binding.etSenderPhone.getText() != null
                ? binding.etSenderPhone.getText().toString().trim() : "";
        String receiverName  = binding.etReceiverName.getText() != null
                ? binding.etReceiverName.getText().toString().trim() : "";
        String receiverPhone = binding.etReceiverPhone.getText() != null
                ? binding.etReceiverPhone.getText().toString().trim() : "";
        String pickup  = binding.etPickup.getText().toString().trim();
        String dropoff = binding.etDropoff.getText().toString().trim();

        if (senderName.isEmpty() || senderPhone.isEmpty()
                || receiverName.isEmpty() || receiverPhone.isEmpty()
                || pickup.isEmpty() || dropoff.isEmpty() || simulatedDistance == 0) {
            Toast.makeText(this, R.string.error_incomplete_info, Toast.LENGTH_SHORT).show();
            return;
        }

        String receiverPhoneNormalized = PhoneUtils.normalizePhone(receiverPhone);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this,
                    "Your session has expired. Please log in again.",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }

        String orderId = "RBT-" + (1000 + new Random().nextInt(9000));
        String userId  = FirebaseAuth.getInstance().getCurrentUser().getUid();

        String commandId = java.util.UUID.randomUUID().toString();
        
        currentBleToken = com.example.autodeliveryapp.ble.BleTokenUtils.generateToken();
        long nowMillis = System.currentTimeMillis();

        Map<String, Object> taskMap = new HashMap<>();
        taskMap.put("orderId",        orderId);
        taskMap.put("senderName",     senderName);
        taskMap.put("senderPhone",    senderPhone);
        taskMap.put("receiverName",   receiverName);
        taskMap.put("receiverPhone",  receiverPhone);
        taskMap.put("receiverPhoneNormalized", receiverPhoneNormalized);
        taskMap.put("pickup",         pickup);
        taskMap.put("dropoff",        dropoff);
        taskMap.put("distance",       simulatedDistance);
        taskMap.put("status",         "pending");
        taskMap.put("createdAt",      nowMillis);
        taskMap.put("robotId",        "defaultRobot");
        taskMap.put("senderUid",      userId);
        
        taskMap.put("bleToken", currentBleToken);
        taskMap.put("bleTokenCreatedAt", nowMillis);
        taskMap.put("bleState", com.example.autodeliveryapp.ble.BleConstants.BLE_STATE_CREATED);
        taskMap.put("senderLoadedConfirmed", false);
        taskMap.put("receiverUnlocked", false);

        // Route/location fields — for TrackingActivity to restore without re-calling OSRM
        taskMap.put("pickupName", pickup);
        taskMap.put("dropoffName", dropoff);
        if (originLatLng != null) {
            taskMap.put("pickupLat", originLatLng.getLatitude());
            taskMap.put("pickupLng", originLatLng.getLongitude());
        }
        if (destinationLatLng != null) {
            taskMap.put("dropoffLat", destinationLatLng.getLatitude());
            taskMap.put("dropoffLng", destinationLatLng.getLongitude());
        }
        taskMap.put("deliveryDistanceKm", simulatedDistance);
        taskMap.put("deliveryDurationMinutes", currentDeliveryDuration > 0
                ? currentDeliveryDuration : (int) Math.ceil(simulatedDistance * 5));
        taskMap.put("deliveryRouteSource", currentRouteSource.isEmpty() ? "pending" : currentRouteSource);
        taskMap.put("deliveryRouteStatus", currentRouteStatus.isEmpty() ? "pending" : currentRouteStatus);
        if (currentRouteGeoJson != null) {
            taskMap.put("deliveryRouteGeoJson", currentRouteGeoJson);
        }
        taskMap.put("activeLeg", "robot_to_pickup");

        // MQTT extras
        taskMap.put("mqttEnabled",    true);
        taskMap.put("mqttCommandId",  commandId);
        taskMap.put("mqttAck",        false);
        taskMap.put("mqttAckMessage", "");
        taskMap.put("lastMqttAt",     nowMillis);

        binding.progressBar.setVisibility(View.VISIBLE);
        freezeForm(true);

        // Temporarily bypass robot slot check for BLE testing
        selectedSlotId = "slot1"; 
        taskMap.put("slotId", selectedSlotId);

        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("tasks")
                .child(userId)
                .child(orderId)
                .setValue(taskMap)
                .addOnSuccessListener(unused -> {
                    // Create sender notification (task_created)
                    createSenderNotification(userId, orderId, pickup, dropoff);

                    lookupReceiverAndNotify(receiverPhoneNormalized, userId, orderId, pickup, dropoff, commandId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CreateTaskDB", "setValue failed: " + e.getMessage());
                    if (binding == null || isFinishing() || isDestroyed()) return;
                    binding.progressBar.setVisibility(View.GONE);
                    freezeForm(false);
                    Toast.makeText(CreateTaskActivity.this,
                            getString(R.string.toast_error_prefix, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Looks up the receiver account by normalized phone number.
     * The task is still created even if the receiver account is not found.
     */
    private void lookupReceiverAndNotify(String phoneNormalized, String senderId,
                                          String orderId, String pickup, String dropoff, String commandId) {
        FirebaseDatabase.getInstance(Constants.DB_URL).getReference("users")
                .orderByChild("phoneNormalized").equalTo(phoneNormalized)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String firstReceiverUid = null;
                            for (DataSnapshot userSnap : snapshot.getChildren()) {
                                String rUid = userSnap.getKey();
                                handleReceiverFound(rUid, senderId, orderId, pickup, dropoff);
                                if (firstReceiverUid == null) firstReceiverUid = rUid;
                            }
                            finishTaskCreation(orderId, senderId, firstReceiverUid, "defaultRobot", selectedSlotId, pickup, dropoff);
                        } else {
                            lookupReceiverTier3(phoneNormalized, senderId, orderId, pickup, dropoff);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.w("CreateTaskDB", "phoneNormalized lookup error: " + error.getMessage());
                        lookupReceiverTier3(phoneNormalized, senderId, orderId, pickup, dropoff);
                    }
                });
    }

    private void lookupReceiverTier3(String phoneNormalized, String senderId,
                                      String orderId, String pickup, String dropoff) {
        FirebaseDatabase.getInstance(Constants.DB_URL).getReference("users")
                .orderByChild("phone").equalTo(phoneNormalized)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String firstReceiverUid = null;
                            for (DataSnapshot userSnap : snapshot.getChildren()) {
                                String rUid = userSnap.getKey();
                                handleReceiverFound(rUid, senderId, orderId, pickup, dropoff);
                                if (firstReceiverUid == null) firstReceiverUid = rUid;
                            }
                            finishTaskCreation(orderId, senderId, firstReceiverUid, "defaultRobot", selectedSlotId, pickup, dropoff);
                        } else {
                            Toast.makeText(getApplicationContext(),
                                    "Receiver has no account, saving receiver info only.",
                                    Toast.LENGTH_SHORT).show();
                            finishTaskCreation(orderId, senderId, null, "defaultRobot", selectedSlotId, pickup, dropoff);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.e("CreateTaskDB", "Receiver lookup failed: " + error.getMessage());
                        finishTaskCreation(orderId, senderId, null, "defaultRobot", selectedSlotId, pickup, dropoff);
                    }
                });
    }

    
    private void handleReceiverFound(String receiverUid, String senderId,
                                      String orderId, String pickup, String dropoff) {
        // 1. recipientTasks/{receiverUid}/{orderId}
        Map<String, Object> recipientTask = new HashMap<>();
        recipientTask.put("senderUid", senderId);
        recipientTask.put("taskId", orderId);
        recipientTask.put("orderId", orderId);
        recipientTask.put("taskPath", "tasks/" + senderId + "/" + orderId);
        recipientTask.put("status", "pending");
        recipientTask.put("createdAt", System.currentTimeMillis());
        recipientTask.put("pickup", pickup);
        recipientTask.put("dropoff", dropoff);
        recipientTask.put("distance", simulatedDistance);
        
        // Copy coordinate and route data so receiver's TrackingActivity can render the map
        if (originLatLng != null) {
            recipientTask.put("pickupLat", originLatLng.getLatitude());
            recipientTask.put("pickupLng", originLatLng.getLongitude());
        }
        if (destinationLatLng != null) {
            recipientTask.put("dropoffLat", destinationLatLng.getLatitude());
            recipientTask.put("dropoffLng", destinationLatLng.getLongitude());
        }
        recipientTask.put("deliveryDistanceKm", simulatedDistance);
        recipientTask.put("deliveryDurationMinutes", currentDeliveryDuration > 0
                ? currentDeliveryDuration : (int) Math.ceil(simulatedDistance * 5));
        recipientTask.put("deliveryRouteSource", currentRouteSource.isEmpty() ? "pending" : currentRouteSource);
        recipientTask.put("deliveryRouteStatus", currentRouteStatus.isEmpty() ? "pending" : currentRouteStatus);
        if (currentRouteGeoJson != null) {
            recipientTask.put("deliveryRouteGeoJson", currentRouteGeoJson);
        }
        
        if (currentBleToken != null) {
            recipientTask.put("bleToken", currentBleToken);
            recipientTask.put("bleTokenCreatedAt", System.currentTimeMillis());
            recipientTask.put("bleState", com.example.autodeliveryapp.ble.BleConstants.BLE_STATE_CREATED);
            recipientTask.put("robotId", "defaultRobot");
            recipientTask.put("slotId", selectedSlotId);
            recipientTask.put("senderLoadedConfirmed", false);
            recipientTask.put("receiverUnlocked", false);
            recipientTask.put("receiverUid", receiverUid);
        }

        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("recipientTasks")
                .child(receiverUid)
                .child(orderId)
                .setValue(recipientTask);

        // Update the main task with the found receiverUid
        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("tasks")
                .child(senderId)
                .child(orderId)
                .child("receiverUid")
                .setValue(receiverUid);

        // 2. notifications/{receiverUid}/{notifId}
        DatabaseReference notifRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("notifications")
                .child(receiverUid)
                .push();

        String now = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(new Date());

        Map<String, Object> notif = new HashMap<>();
        notif.put("icon", NotificationUtils.getIcon(NotificationUtils.TYPE_INCOMING_DELIVERY));
        notif.put("title", NotificationUtils.buildTitle(NotificationUtils.TYPE_INCOMING_DELIVERY));
        notif.put("message", NotificationUtils.buildMessage(NotificationUtils.TYPE_INCOMING_DELIVERY, orderId, null, pickup, dropoff));
        notif.put("time", now);
        notif.put("taskId", orderId);
        notif.put("orderId", orderId);
        notif.put("pickup", pickup);
        notif.put("dropoff", dropoff);
        notif.put("distance", simulatedDistance);
        notif.put("senderUid", senderId);
        notif.put("type", NotificationUtils.TYPE_INCOMING_DELIVERY);
        notif.put("read", false);
        notif.put("pushed", false);
        notif.put("createdAt", System.currentTimeMillis());

        notifRef.setValue(notif).addOnFailureListener(e -> {
            android.util.Log.e("CreateTaskDB", "Failed to write receiver notification: " + e.getMessage());
            Toast.makeText(getApplicationContext(),
                    "Task created, but notification to receiver failed.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Create notification for sender when task is created successfully.
     */
    private void createSenderNotification(String senderId, String orderId,
                                           String pickup, String dropoff) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("notifications")
                .child(senderId)
                .push();

        String now = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(new Date());

        Map<String, Object> notif = new HashMap<>();
        notif.put("icon", NotificationUtils.getIcon(NotificationUtils.TYPE_TASK_CREATED));
        notif.put("title", NotificationUtils.buildTitle(NotificationUtils.TYPE_TASK_CREATED));
        notif.put("message", NotificationUtils.buildMessage(NotificationUtils.TYPE_TASK_CREATED, orderId, null, pickup, dropoff));
        notif.put("time", now);
        notif.put("taskId", orderId);
        notif.put("orderId", orderId);
        notif.put("pickup", pickup);
        notif.put("dropoff", dropoff);
        notif.put("distance", simulatedDistance);
        notif.put("senderUid", senderId);
        notif.put("type", NotificationUtils.TYPE_TASK_CREATED);
        notif.put("read", false);
        notif.put("pushed", false);
        notif.put("createdAt", System.currentTimeMillis());

        notifRef.setValue(notif).addOnFailureListener(e ->
                android.util.Log.w("CreateTaskDB", "Sender notification write failed: " + e.getMessage()));
    }



    /**
     * Opens TrackingActivity after task creation and passes all required task extras.
     */
    private void finishTaskCreation(String orderId, String senderUid, String receiverUid, String robotId, String slotId, String pickup, String dropoff) {
        if (binding == null || isFinishing() || isDestroyed()) return;
        binding.progressBar.setVisibility(View.GONE);
        Toast.makeText(CreateTaskActivity.this, R.string.toast_task_created, Toast.LENGTH_SHORT).show();


        Intent intent = new Intent(CreateTaskActivity.this, TrackingActivity.class);
        intent.putExtra("orderId",  orderId);
        intent.putExtra("taskId",  orderId);
        intent.putExtra("senderUid", senderUid);
        if (receiverUid != null) {
            intent.putExtra("receiverUid", receiverUid);
        }
        intent.putExtra("robotId", robotId);
        intent.putExtra("slotId", slotId);
        intent.putExtra("pickup",   pickup);
        intent.putExtra("dropoff",  dropoff);
        intent.putExtra("distance", simulatedDistance);
        startActivity(intent);
        finish();
    }





    @Override protected void onStart()  { super.onStart();  binding.mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); binding.mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  binding.mapView.onPause(); }
    @Override protected void onStop()   { super.onStop();   binding.mapView.onStop(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) binding.mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (binding != null) binding.mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);

        mapStyle   = null;
        routeGeoJson = null;
        cachedMap  = null;
        if (binding != null) binding.mapView.onDestroy();
        binding = null; // Release binding to avoid memory leak
    }
}




