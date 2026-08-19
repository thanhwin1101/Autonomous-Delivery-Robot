package com.example.autodeliveryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autodeliveryapp.databinding.ActivityTrackingBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// MQTT imports removed
import com.google.firebase.auth.FirebaseAuth;


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

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import androidx.annotation.NonNull;
import android.graphics.Color;

import com.example.autodeliveryapp.ble.BleActionCallback;
import com.example.autodeliveryapp.ble.BleConstants;
import com.example.autodeliveryapp.ble.BleManager;
import com.example.autodeliveryapp.ble.BlePermissionHelper;
import com.example.autodeliveryapp.utils.TaskStatus;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class TrackingActivity extends AppCompatActivity {
    

    private ActivityTrackingBinding binding;



    private Style mapStyle = null;
    private String routeGeoJson = null;        // Currently drawn route on map
    private String deliveryRouteGeoJson = null; // Saved A→B route from Firebase
    private MapLibreMap cachedMap = null;


    // Pickup A and Delivery B coordinates (resolved from Firebase)
    private LatLng pickupLatLng;     // Point A
    private LatLng dropoffLatLng;    // Point B

    // Robot current location C (from MQTT telemetry)
    private LatLng robotLatLng = null; // Point C — null until first telemetry received
    private Float robotHeading = 0f; // Robot heading/yaw
    
    private LatLng homeLatLng = null; // AGV Home Location

    private final Executor networkExecutor = Executors.newSingleThreadExecutor();

    private String currentOrderId;
    private String currentRobotId;


    private String currentBleToken = null;
    private String currentBleState = null;
    private String currentTaskStatus = null;
    private String currentActiveLeg = null;
    private String currentSenderName = "";
    private String currentSenderUid = null;
    private String currentReceiverUid = null;
    private boolean isSender = false;
    private boolean isReceiverTokenVerified = false;
    private BleManager bleManager;
    private ValueEventListener taskListener;
    private DatabaseReference taskRef;

    // Flag to prevent duplicate OSRM calls if Firebase already has the data
    private boolean routeRestoredFromFirebase = false;
    private boolean hasFetchedLiveRoute = false;
    private boolean hasCenteredMap = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        binding = ActivityTrackingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);


        binding.mapView.onCreate(savedInstanceState);


        // BottomSheet receive navigation bar padding
        EdgeToEdgeHelper.applyStatusBarPadding(binding.btnBackWrapper, binding.titleOverlay);
        EdgeToEdgeHelper.applyNavigationBarPadding(binding.bottomSheet);


        String orderId  = getIntent().getStringExtra("orderId");
        String pickup   = getIntent().getStringExtra("pickup");
        String dropoff  = getIntent().getStringExtra("dropoff");
        // NOTE: distance from Intent is a hint only; Firebase is the authoritative source
        double intentDistance = getIntent().getDoubleExtra("distance", 0.0);

        if (orderId == null) orderId = "---";
        if (pickup  == null) pickup  = "---";
        if (dropoff == null) dropoff = "---";

        currentOrderId = orderId;

        String robotId    = getIntent().getStringExtra("robotId");
        String slotId     = getIntent().getStringExtra("slotId");
        String senderUid  = getIntent().getStringExtra("senderUid");
        String receiverUid = getIntent().getStringExtra("receiverUid");

        // Fallback robotId
        if (robotId == null || robotId.isEmpty()) {
            robotId = "defaultRobot";
            android.util.Log.w("TrackingActivity", "robotId is null in intent, using fallback: defaultRobot");
        }
        currentRobotId = robotId;


        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "guest";
        if (senderUid == null || senderUid.isEmpty()) senderUid = currentUid;

        isSender   = currentUid.equals(senderUid);
        bleManager = new BleManager(this);

        // Firebase listener — reads ALL task fields including route/distance
        setupFirebaseListener(currentUid, senderUid);

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

        binding.btnBleAction.setOnClickListener(v -> handleBleAction());
        
        if (binding.btnCancelOrder != null) {
            binding.btnCancelOrder.setOnClickListener(v -> handleCancelOrder());
        }
        if (binding.btnEditReceiver != null) {
            binding.btnEditReceiver.setOnClickListener(v -> handleEditReceiver());
        }

        // MQTT connection removed, hide status text
        binding.tvMqttStatus.setVisibility(View.GONE);

        // Set static header fields
        binding.tvMissionId.setText(getString(R.string.format_mission_id, orderId));
        binding.tvOrderId.setText(getString(R.string.format_order_id, orderId));

        // Show initial distance from Intent as a provisional value while Firebase loads.
        // Firebase snapshot callback will overwrite these if better data exists.
        if (intentDistance > 0) {
            binding.tvDistanceRemaining.setText(
                    getString(R.string.format_distance_km, String.valueOf(intentDistance)));
            int etaMinutes = (int) Math.ceil(intentDistance * 5);
            binding.tvETA.setText(getString(R.string.format_eta_minutes, etaMinutes));
        } else {
            binding.tvDistanceRemaining.setText("---");
            binding.tvETA.setText("---");
        }

        // Initial timeline: show "Order Received" timestamp and neutral status
        String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        binding.tvReceivedTime.setText(now + " - " + pickup);
        // Do NOT set "Moving to B" here — updateStatusUI() will set the correct text
        // after Firebase loads currentTaskStatus. Default to pending state.
        updateStatusUI(TaskStatus.PENDING);

        BottomSheetBehavior<android.view.View> bsb = BottomSheetBehavior.from(binding.bottomSheet);
        bsb.setState(BottomSheetBehavior.STATE_COLLAPSED);

        binding.btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // Resolve coordinates from address names (for initial map center; Firebase may override)
        pickupLatLng  = getCoordinates(pickup);
        dropoffLatLng = getCoordinates(dropoff);

        // Initialize map
        binding.mapView.getMapAsync(map -> {
            cachedMap = map;
            LatLng initialCenter = pickupLatLng != null ? pickupLatLng : getInitialMapCenter();
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialCenter, 12.0f));
            map.setStyle(Constants.MAP_STYLE_URL, style -> {

                if (binding == null || isFinishing() || isDestroyed()) return;
                mapStyle = style;
                android.util.Log.d("TrackingMap", "Map style loaded");
                enableLocationComponent(style);
                if (routeGeoJson == null && deliveryRouteGeoJson != null) {
                    routeGeoJson = deliveryRouteGeoJson;
                }
                if (routeGeoJson != null) {
                    drawRouteOnMap();
                }
            });
        });

        // OSRM fetch is deferred: setupFirebaseListener will decide whether to fetch or restore
    }


    // ─────────────────────────────────────────────────────────────────
    // Status → UI mapping
    // ─────────────────────────────────────────────────────────────────

    /**
     * Updates all status-dependent UI elements based on the current task status.
     * This is the single authoritative method for rendering delivery phase text.
     */
    private void updateStatusUI(String status) {
        if (binding == null) return;
        if (status == null) status = TaskStatus.PENDING;

        android.util.Log.d("TrackingStatus", "updateStatusUI: " + status);

        // ── Timeline step colours ──────────────────────────────────────
        // Step 1 (received) is always active (green) once we have an order
        setStepActive(binding.stepIconReceived, true);

        switch (status) {
            case TaskStatus.PENDING:
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, false);
                setStepActive(binding.stepIconDelivering, false);
                setStepActive(binding.stepIconCompleted, false);
                
                binding.tvHeaderStatus.setText("PENDING");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.text_secondary));
                
                binding.tvStepPendingLabel.setText("Pending Assignment");
                binding.tvStepPendingSub.setText("Waiting for robot assignment...");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_hint));
                binding.tvStepDeliveringLabel.setTextColor(getColor(R.color.text_hint));
                binding.tvDeliveryStatus.setText("Waiting for robot assignment...");
                break;

            case TaskStatus.GOING_TO_PICKUP:
                // Phase: robot going to pickup A
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, true);
                setStepActive(binding.stepIconDelivering, false);
                setStepActive(binding.stepIconCompleted, false);

                binding.tvHeaderStatus.setText("HEADING TO PICKUP");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.primary_green));

                binding.tvStepPendingLabel.setText("Assignment Confirmed");
                binding.tvStepPendingSub.setText("Robot assigned successfully");
                binding.tvStepPreparingLabel.setText("Going to Pickup");
                binding.tvStepPreparingSub.setText("Robot heading to pickup point (A)");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_primary));
                binding.tvStepDeliveringLabel.setTextColor(getColor(R.color.text_hint));
                binding.tvDeliveryStatus.setText(robotLatLng != null
                        ? "Robot is on the way to pickup (A)"
                        : "Waiting for robot location...");
                break;

            case TaskStatus.ARRIVED_PICKUP:
            case TaskStatus.WAITING_SENDER_LOAD:
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, true);
                setStepActive(binding.stepIconDelivering, false);
                setStepActive(binding.stepIconCompleted, false);

                binding.tvHeaderStatus.setText("WAITING FOR LOAD");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.primary_green));

                binding.tvStepPendingLabel.setText("Assignment Confirmed");
                binding.tvStepPendingSub.setText("Robot assigned successfully");
                binding.tvStepPreparingLabel.setText("At Pickup Point");
                binding.tvStepPreparingSub.setText("Robot arrived at pickup (A) — awaiting load");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_primary));
                binding.tvDeliveryStatus.setText("Waiting for sender to load item");
                break;

            case TaskStatus.SENDER_LOADED:
            case TaskStatus.PICKED_UP:
            case TaskStatus.GOING_TO_DESTINATION:
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, true);
                setStepActive(binding.stepIconDelivering, true);
                setStepActive(binding.stepIconCompleted, false);

                binding.tvHeaderStatus.setText("ACTIVE DELIVERY");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.primary_green));

                binding.tvStepPendingLabel.setText("Assignment Confirmed");
                binding.tvStepPendingSub.setText("Robot assigned successfully");
                binding.tvStepPreparingLabel.setText("Picked Up");
                binding.tvStepPreparingSub.setText("Item loaded — robot heading to delivery (B)");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_primary));
                binding.tvStepDeliveringLabel.setText("Delivering");
                binding.tvStepDeliveringLabel.setTextColor(getColor(R.color.primary_green));
                binding.tvDeliveryStatus.setText("Robot delivering to destination (B)");
                break;

            case TaskStatus.ARRIVED_DROPOFF:
            case TaskStatus.WAITING_RECEIVER_UNLOCK:
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, true);
                setStepActive(binding.stepIconDelivering, true);
                setStepActive(binding.stepIconCompleted, false);

                binding.tvHeaderStatus.setText("WAITING AT DESTINATION");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.primary_green));

                binding.tvStepPendingLabel.setText("Assignment Confirmed");
                binding.tvStepPendingSub.setText("Robot assigned successfully");
                binding.tvStepPreparingLabel.setText("Picked Up");
                binding.tvStepPreparingSub.setText("Item delivered — awaiting receiver");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_primary));
                binding.tvStepDeliveringLabel.setText("Arrived at Delivery");
                binding.tvStepDeliveringLabel.setTextColor(getColor(R.color.primary_green));
                binding.tvDeliveryStatus.setText("Robot waiting at destination (B)");
                break;

            case TaskStatus.DELIVERED:
                setStepActive(binding.stepIconPending, true);
                setStepActive(binding.stepIconPreparing, true);
                setStepActive(binding.stepIconDelivering, true);
                setStepActive(binding.stepIconCompleted, true);

                binding.tvHeaderStatus.setText("DELIVERED");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.primary_green));

                binding.tvStepPendingLabel.setText("Assignment Confirmed");
                binding.tvStepPendingSub.setText("Robot assigned successfully");
                binding.tvStepPreparingLabel.setText("Picked Up");
                binding.tvStepPreparingSub.setText("Delivery complete");
                binding.tvStepPreparingLabel.setTextColor(getColor(R.color.text_primary));
                binding.tvStepDeliveringLabel.setText("Delivered");
                binding.tvStepDeliveringLabel.setTextColor(getColor(R.color.primary_green));
                binding.tvDeliveryStatus.setText("Package delivered successfully!");
                binding.tvStepCompletedLabel.setText("Delivered ✓");
                binding.tvStepCompletedLabel.setTextColor(getColor(R.color.primary_green));
                binding.tvStepCompletedSub.setText("Order complete");
                break;

            case TaskStatus.CANCELLED:
                setStepActive(binding.stepIconPending, false);
                setStepActive(binding.stepIconPreparing, false);
                setStepActive(binding.stepIconDelivering, false);
                setStepActive(binding.stepIconCompleted, false);
                
                binding.tvHeaderStatus.setText("CANCELLED");
                binding.tvHeaderStatus.setTextColor(getColor(R.color.danger_red));
                
                binding.tvDeliveryStatus.setText("Order cancelled");
                binding.tvStepCompletedLabel.setText("Cancelled");
                break;

            default:
                // Unknown status — show raw value for debugging
                binding.tvDeliveryStatus.setText("Status: " + status);
                break;
        }

        // Update route on map based on current leg + robot position
        updateMapRoute(status);
    }

    /** Sets the background tint of a step icon FrameLayout to green (active) or grey (inactive). */
    private void setStepActive(android.widget.FrameLayout icon, boolean active) {
        if (icon == null) return;
        icon.setBackgroundColor(active
                ? getColor(R.color.primary_green)
                : Color.parseColor("#DDDDDD"));
    }


    // ─────────────────────────────────────────────────────────────────
    // Route management (C→A→B model)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Decides which route to draw based on current status + robot location.
     *
     * Before pickup (pending / going_to_pickup):
     *   - If robotLat/Lng available: draw C→A (live route to pickup)
     *   - Else: draw A→B preview (dashed look, no robot)
     *
     * After pickup (going_to_destination and beyond):
     *   - If robotLat/Lng available: draw C→B (live delivery route)
     *   - Else: draw A→B preview fallback
     */
    private void updateMapRoute(String status) {
        if (mapStyle == null || pickupLatLng == null || dropoffLatLng == null) return;

        // To prevent OSRM rate limiting (API spam), we rely entirely on the static A->B delivery route
        // fetched during task creation. The drawRouteOnMap() method automatically handles splitting
        // this route into traversed/remaining segments based on the current robotLatLng.
        if (deliveryRouteGeoJson != null) {
            routeGeoJson = deliveryRouteGeoJson;
            drawRouteOnMap();
        }
    }

    /**
     * Draws the current routeGeoJson on the map, along with:
     * - Green circle at pickup point A
     * - Red circle at delivery point B
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

    private void drawRouteOnMap() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        if (mapStyle == null) return;
        if (pickupLatLng == null || dropoffLatLng == null) return;

        try {
            final String SOURCE_ROUTE    = "route-source";
            final String LAYER_ROUTE     = "route-layer";
            final String SOURCE_MARKER_A = "marker-a-source";
            final String LAYER_MARKER_A  = "marker-a-layer";
            final String SOURCE_MARKER_B = "marker-b-source";
            final String LAYER_MARKER_B  = "marker-b-layer";

            // Remove stale layers/sources
            if (mapStyle.getLayer("route-traversed-layer") != null) mapStyle.removeLayer("route-traversed-layer");
            if (mapStyle.getLayer("route-remaining-layer") != null) mapStyle.removeLayer("route-remaining-layer");
            if (mapStyle.getLayer(LAYER_MARKER_A) != null) mapStyle.removeLayer(LAYER_MARKER_A);
            if (mapStyle.getLayer(LAYER_MARKER_B) != null) mapStyle.removeLayer(LAYER_MARKER_B);
            if (mapStyle.getSource("route-traversed-source") != null) mapStyle.removeSource("route-traversed-source");
            if (mapStyle.getSource("route-remaining-source") != null) mapStyle.removeSource("route-remaining-source");
            if (mapStyle.getSource(SOURCE_MARKER_A) != null) mapStyle.removeSource(SOURCE_MARKER_A);
            if (mapStyle.getSource(SOURCE_MARKER_B) != null) mapStyle.removeSource(SOURCE_MARKER_B);

            // Split and draw route polyline
            try {
                if (routeGeoJson != null) {
                    org.json.JSONObject root = new org.json.JSONObject(routeGeoJson);
                    org.json.JSONArray coords = root.getJSONObject("geometry").getJSONArray("coordinates");
                    
                    int closestIdx = 0;
                if (robotLatLng != null) {
                    double minDist = Double.MAX_VALUE;
                    for (int i = 0; i < coords.length(); i++) {
                        org.json.JSONArray pt = coords.getJSONArray(i);
                        double lng = pt.getDouble(0);
                        double lat = pt.getDouble(1);
                        double dist = haversineKm(robotLatLng, new LatLng(lat, lng));
                        if (dist < minDist) {
                            minDist = dist;
                            closestIdx = i;
                        }
                    }
                }

                org.json.JSONArray traversedCoords = new org.json.JSONArray();
                org.json.JSONArray remainingCoords = new org.json.JSONArray();

                if (robotLatLng != null) {
                    org.json.JSONArray rPt = new org.json.JSONArray();
                    rPt.put(robotLatLng.getLongitude());
                    rPt.put(robotLatLng.getLatitude());
                    remainingCoords.put(rPt);
                }

                for (int i = 0; i < coords.length(); i++) {
                    if (i <= closestIdx) traversedCoords.put(coords.getJSONArray(i));
                    if (i >= closestIdx) remainingCoords.put(coords.getJSONArray(i));
                }

                if (robotLatLng != null) {
                    org.json.JSONArray rPt = new org.json.JSONArray();
                    rPt.put(robotLatLng.getLongitude());
                    rPt.put(robotLatLng.getLatitude());
                    traversedCoords.put(rPt);
                }

                if (traversedCoords.length() > 1) {
                    org.json.JSONObject traversedGeo = new org.json.JSONObject(routeGeoJson);
                    traversedGeo.getJSONObject("geometry").put("coordinates", traversedCoords);
                    mapStyle.addSource(new GeoJsonSource("route-traversed-source", traversedGeo.toString()));
                    mapStyle.addLayer(new LineLayer("route-traversed-layer", "route-traversed-source")
                            .withProperties(
                                    PropertyFactory.lineColor("#9CA3AF"),
                                    PropertyFactory.lineWidth(5f),
                                    PropertyFactory.lineOpacity(0.85f)));
                }

                if (remainingCoords.length() > 1) {
                    org.json.JSONObject remainingGeo = new org.json.JSONObject(routeGeoJson);
                    remainingGeo.getJSONObject("geometry").put("coordinates", remainingCoords);
                    mapStyle.addSource(new GeoJsonSource("route-remaining-source", remainingGeo.toString()));
                    mapStyle.addLayer(new LineLayer("route-remaining-layer", "route-remaining-source")
                            .withProperties(
                                    PropertyFactory.lineColor("#10B981"),
                                    PropertyFactory.lineWidth(6f),
                                    PropertyFactory.lineOpacity(1.0f)));
                }
                } // end if routeGeoJson != null
            } catch (Exception e) {
                android.util.Log.e("TrackingMap", "Failed to split route", e);
            }

            // Pickup Marker A — green
            mapStyle.addSource(new GeoJsonSource(SOURCE_MARKER_A, buildPointGeoJson(pickupLatLng)));
            mapStyle.addLayer(new CircleLayer(LAYER_MARKER_A, SOURCE_MARKER_A)
                    .withProperties(
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleColor("#3B82F6"),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF")));

            // Delivery Marker B — red
            mapStyle.addSource(new GeoJsonSource(SOURCE_MARKER_B, buildPointGeoJson(dropoffLatLng)));
            mapStyle.addLayer(new CircleLayer(LAYER_MARKER_B, SOURCE_MARKER_B)
                    .withProperties(
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleColor("#EF4444"),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF")));

            // Current Robot Marker
            if (robotLatLng != null) {
                final String SOURCE_ROBOT = "robot-source";
                final String LAYER_ROBOT  = "robot-layer";
                final String LAYER_ROBOT_HEADING = "robot-heading-layer"; // Retained to clear old layers if any
                if (mapStyle.getLayer(LAYER_ROBOT_HEADING) != null) mapStyle.removeLayer(LAYER_ROBOT_HEADING);
                if (mapStyle.getLayer(LAYER_ROBOT) != null) mapStyle.removeLayer(LAYER_ROBOT);
                if (mapStyle.getSource(SOURCE_ROBOT) != null) mapStyle.removeSource(SOURCE_ROBOT);

                // Ensure the custom AGV icon is loaded into the map style
                if (mapStyle.getImage("agv-icon") == null) {
                    android.graphics.Bitmap agvIcon = getBitmapFromVectorDrawable(this, R.drawable.ic_location_cone);
                    if (agvIcon != null) {
                        mapStyle.addImage("agv-icon", agvIcon);
                    }
                }

                mapStyle.addSource(new GeoJsonSource(SOURCE_ROBOT, buildPointGeoJson(robotLatLng)));
                
                // Use a SymbolLayer with the custom AGV icon instead of a CircleLayer + TextLayer
                mapStyle.addLayer(new org.maplibre.android.style.layers.SymbolLayer(LAYER_ROBOT, SOURCE_ROBOT)
                        .withProperties(
                                PropertyFactory.iconImage("agv-icon"),
                                PropertyFactory.iconSize(1.2f),
                                PropertyFactory.iconRotate(robotHeading),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true)
                        ));
            }

            // Draw Home location if available
            if (homeLatLng != null) {
                if (mapStyle.getImage("home-icon") == null) {
                    android.graphics.Bitmap homeIcon = getBitmapFromVectorDrawable(this, R.drawable.ic_nav_home);
                    if (homeIcon != null) {
                        // Apply tint if needed to make it blue
                        android.graphics.Canvas canvas = new android.graphics.Canvas(homeIcon);
                        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_home);
                        if (drawable != null) {
                            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                            androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.parseColor("#3B82F6"));
                            drawable.draw(canvas);
                        }
                        mapStyle.addImage("home-icon", homeIcon);
                    }
                }

                if (mapStyle.getSource("source-home") == null) {
                    mapStyle.addSource(new GeoJsonSource("source-home", buildPointGeoJson(homeLatLng)));
                    mapStyle.addLayer(new org.maplibre.android.style.layers.SymbolLayer("layer-home", "source-home")
                            .withProperties(
                                    PropertyFactory.iconImage("home-icon"),
                                    PropertyFactory.iconSize(1.5f),
                                    PropertyFactory.iconAllowOverlap(true)
                            ));
                } else {
                    ((GeoJsonSource) mapStyle.getSource("source-home")).setGeoJson(buildPointGeoJson(homeLatLng));
                }
            }

            // Camera bounds to fit both markers
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder()
                    .include(pickupLatLng)
                    .include(dropoffLatLng);
            if (robotLatLng != null) boundsBuilder.include(robotLatLng);
            if (homeLatLng != null) boundsBuilder.include(homeLatLng);

            final LatLngBounds bounds = boundsBuilder.build();
            binding.mapView.post(() -> {
                if (binding == null || isFinishing() || isDestroyed() || cachedMap == null) return;
                
                if (!hasCenteredMap) {
                    try {
                        cachedMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
                        hasCenteredMap = true;
                    } catch (Exception e) {
                        android.util.Log.w("TrackingMap", "bounds zoom failed: " + e.getMessage());
                        try {
                            cachedMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 13));
                            hasCenteredMap = true;
                        } catch (Exception ex) {}
                    }
                }
            });

            android.util.Log.d("TrackingMap", "Route drawn successfully");

        } catch (Exception e) {
            android.util.Log.e("TrackingMap", "drawRouteOnMap error: " + e.getMessage());
        }
    }

    /** Builds a minimal GeoJSON FeatureCollection string for a single Point. */
    private String buildPointGeoJson(LatLng point) {
        try {
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
            return collection.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private android.graphics.Bitmap getBitmapFromVectorDrawable(android.content.Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


    // ─────────────────────────────────────────────────────────────────
    // Address resolution helpers
    // ─────────────────────────────────────────────────────────────────

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

    private LatLng getCoordinates(String address) {
        if (address == null || address.trim().isEmpty())
            return new LatLng(16.0611, 108.2275);
        String normalized = normalizeAddress(address);
        for (Map.Entry<String, double[]> entry : Constants.DA_NANG_LOCATIONS.entrySet()) {
            String keyNormalized = normalizeAddress(entry.getKey());
            if (normalized.contains(keyNormalized) || keyNormalized.contains(normalized)) {
                double[] coords = entry.getValue();
                return new LatLng(coords[0], coords[1]);
            }
        }
        return new LatLng(16.0611, 108.2275);
    }

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

    private String buildStraightLineGeoJson(LatLng origin, LatLng destination) {
        try {
            JSONObject feature  = new JSONObject();
            feature.put("type", "Feature");
            JSONObject geometry = new JSONObject();
            geometry.put("type", "LineString");
            JSONArray coords = new JSONArray();
            JSONArray oCoord = new JSONArray();
            oCoord.put(origin.getLongitude()); oCoord.put(origin.getLatitude());
            JSONArray dCoord = new JSONArray();
            dCoord.put(destination.getLongitude()); dCoord.put(destination.getLatitude());
            coords.put(oCoord); coords.put(dCoord);
            geometry.put("coordinates", coords);
            feature.put("geometry", geometry);
            feature.put("properties", new JSONObject());
            return feature.toString();
        } catch (Exception e) {
            android.util.Log.e("TrackingOSRM", "buildStraightLine error: " + e.getMessage());
            return null;
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // Firebase listener — primary source of truth for all task data
    // ─────────────────────────────────────────────────────────────────

    private void setupFirebaseListener(String currentUid, String senderUid) {
        if (isSender) {
            taskRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                    .getReference("tasks").child(senderUid).child(currentOrderId);
        } else {
            taskRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                    .getReference("recipientTasks").child(currentUid).child(currentOrderId);
        }

        taskListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || binding == null) return;

                // ── Status & activeLeg ────────────────────────────────
                currentTaskStatus = snapshot.child("status").getValue(String.class);
                currentActiveLeg  = snapshot.child("activeLeg").getValue(String.class);

                // ── BLE fields ────────────────────────────────────────
                currentBleToken = snapshot.child("bleToken").getValue(String.class);
                currentBleState = snapshot.child("bleState").getValue(String.class);
                String updatedBleToken = snapshot.child("bleToken").getValue(String.class);
                String sName = snapshot.child("senderName").getValue(String.class);
                if (sName != null) currentSenderName = sName;
                
                String sUid = snapshot.child("senderUid").getValue(String.class);
                if (sUid != null) currentSenderUid = sUid;
                
                String rUid = snapshot.child("receiverUid").getValue(String.class);
                if (rUid != null) currentReceiverUid = rUid;

                // ── Route/location restoration (Firebase priority) ─────
                // 1. Restore pickup A coordinates
                Double fbPickupLat = snapshot.child("pickupLat").getValue(Double.class);
                Double fbPickupLng = snapshot.child("pickupLng").getValue(Double.class);
                if (fbPickupLat != null && fbPickupLng != null) {
                    pickupLatLng = new LatLng(fbPickupLat, fbPickupLng);
                }

                // 2. Restore dropoff B coordinates
                Double fbDropoffLat = snapshot.child("dropoffLat").getValue(Double.class);
                Double fbDropoffLng = snapshot.child("dropoffLng").getValue(Double.class);
                if (fbDropoffLat != null && fbDropoffLng != null) {
                                    dropoffLatLng = new LatLng(fbDropoffLat, fbDropoffLng);
                }

                // 3. Restore A→B route GeoJSON (no OSRM call needed if this exists)
                String fbRouteGeoJson = snapshot.child("deliveryRouteGeoJson").getValue(String.class);
                if (fbRouteGeoJson != null) {
                    try {
                        org.json.JSONObject root = new org.json.JSONObject(fbRouteGeoJson);
                        org.json.JSONArray coords = root.getJSONObject("geometry").getJSONArray("coordinates");
                        if (coords.length() > 2) {
                            deliveryRouteGeoJson = fbRouteGeoJson;
                            routeRestoredFromFirebase = true;
                        } else {
                            android.util.Log.d("Tracking", "Firebase route is straight line fallback, ignoring");
                        }
                    } catch (Exception e) {
                        deliveryRouteGeoJson = fbRouteGeoJson;
                        routeRestoredFromFirebase = true;
                    }
                }

                // 4. Restore distance/ETA (Firebase overrides Intent)
                Double fbDistanceKm = snapshot.child("deliveryDistanceKm").getValue(Double.class);
                if (fbDistanceKm == null || fbDistanceKm == 0) {
                    // fallback: try legacy "distance" field
                    fbDistanceKm = snapshot.child("distance").getValue(Double.class);
                }
                Long fbDurationMin = snapshot.child("deliveryDurationMinutes").getValue(Long.class);

                if (fbDistanceKm != null && fbDistanceKm > 0) {
                    final double distKm = fbDistanceKm;
                    final int    etaMin = (fbDurationMin != null && fbDurationMin > 0)
                            ? fbDurationMin.intValue()
                            : (int) Math.ceil(distKm * 5);
                    binding.tvDistanceRemaining.setText(
                            getString(R.string.format_distance_km, String.valueOf(distKm)));
                    binding.tvETA.setText(getString(R.string.format_eta_minutes, etaMin));
                } else if (!routeRestoredFromFirebase && pickupLatLng != null && dropoffLatLng != null) {
                    // No distance in Firebase — recalculate via OSRM or Haversine
                    recalculateDistanceFromCoords();
                }

                // 5. Robot current position C (from telemetry written by MQTT bridge)
                Double fbRobotLat = snapshot.child("robotLat").getValue(Double.class);
                Double fbRobotLng = snapshot.child("robotLng").getValue(Double.class);
                Double fbRobotHeading = snapshot.child("robotHeading").getValue(Double.class);
                if (fbRobotLat != null && fbRobotLng != null
                        && fbRobotLat != 0 && fbRobotLng != 0) {
                    robotLatLng = new LatLng(fbRobotLat, fbRobotLng);
                }
                if (fbRobotHeading != null) {
                    robotHeading = fbRobotHeading.floatValue();
                }

                // 6. Parse AGV Home location
                Double fbHomeLat = snapshot.child("homeLat").getValue(Double.class);
                Double fbHomeLng = snapshot.child("homeLng").getValue(Double.class);
                if (fbHomeLat != null && fbHomeLng != null && fbHomeLat != 0 && fbHomeLng != 0) {
                    homeLatLng = new LatLng(fbHomeLat, fbHomeLng);
                }

                // ── Update UI ─────────────────────────────────────────
                updateStatusUI(currentTaskStatus);
                updateBleButtonVisibility();

                // ── Draw map (if style already loaded) ────────────────
                if (mapStyle != null) {
                    if (routeGeoJson == null && deliveryRouteGeoJson != null) {
                        routeGeoJson = deliveryRouteGeoJson;
                    }
                    drawRouteOnMap();
                }

                // ── If no route data at all, trigger OSRM fetch ───────
                if (!routeRestoredFromFirebase && deliveryRouteGeoJson == null
                        && pickupLatLng != null && dropoffLatLng != null) {
                    recalculateDistanceFromCoords();
                } else if (!hasFetchedLiveRoute && robotLatLng != null && pickupLatLng != null && dropoffLatLng != null) {
                    hasFetchedLiveRoute = true;
                    recalculateDistanceFromCoords();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("TrackingActivity", "taskListener failed: " + error.getMessage());
            }
        };
        taskRef.addValueEventListener(taskListener);
    }

    /**
     * Recalculates A→B distance via OSRM (or Haversine fallback) when Firebase
     * doesn't have distance data. Updates Firebase with result for future re-opens.
     */
    private void recalculateDistanceFromCoords() {
        if (pickupLatLng == null || dropoffLatLng == null) return;
        networkExecutor.execute(() -> {
            String urlStr;
            if (robotLatLng != null && robotLatLng.getLatitude() != 0 && robotLatLng.getLongitude() != 0) {
                urlStr = String.format(Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f;%f,%f?overview=full&geometries=geojson",
                        robotLatLng.getLongitude(), robotLatLng.getLatitude(),
                        pickupLatLng.getLongitude(), pickupLatLng.getLatitude(),
                        dropoffLatLng.getLongitude(), dropoffLatLng.getLatitude());
            } else {
                urlStr = String.format(Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                        pickupLatLng.getLongitude(), pickupLatLng.getLatitude(),
                        dropoffLatLng.getLongitude(), dropoffLatLng.getLatitude());
            }
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "AutoDeliveryApp/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    double hav = haversineKm(pickupLatLng, dropoffLatLng);
                    deliveryRouteGeoJson = buildStraightLineGeoJson(pickupLatLng, dropoffLatLng);
                    routeGeoJson = deliveryRouteGeoJson;
                    displayDistanceAndETA(hav, (int) Math.ceil(hav * 5));
                    runOnUiThread(this::drawRouteOnMap);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject root    = new JSONObject(sb.toString());
                JSONObject route0  = root.getJSONArray("routes").getJSONObject(0);
                double distanceKm  = Math.round(route0.getDouble("distance") / 100.0) / 10.0;
                int    etaMin      = (int) Math.ceil(distanceKm * 5);

                JSONObject geometry = route0.getJSONObject("geometry");
                JSONObject feature  = new JSONObject();
                feature.put("type", "Feature");
                feature.put("geometry", geometry);
                feature.put("properties", new JSONObject());
                deliveryRouteGeoJson = feature.toString();
                routeGeoJson = deliveryRouteGeoJson;

                displayDistanceAndETA(distanceKm, etaMin);
                runOnUiThread(this::drawRouteOnMap);

            } catch (Exception e) {
                android.util.Log.e("TrackingOSRM", "recalculate failed: " + e.getMessage());
                double hav = haversineKm(pickupLatLng, dropoffLatLng);
                deliveryRouteGeoJson = buildStraightLineGeoJson(pickupLatLng, dropoffLatLng);
                routeGeoJson = deliveryRouteGeoJson;
                displayDistanceAndETA(hav, (int) Math.ceil(hav * 5));
                runOnUiThread(this::drawRouteOnMap);
            }
        });
    }

    private void displayDistanceAndETA(double distanceKm, int etaMin) {
        runOnUiThread(() -> {
            if (binding == null || isFinishing() || isDestroyed()) return;
            binding.tvDistanceRemaining.setText(
                    getString(R.string.format_distance_km, String.valueOf(distanceKm)));
            binding.tvETA.setText(getString(R.string.format_eta_minutes, etaMin));
        });
    }


    // ─────────────────────────────────────────────────────────────────
    // BLE logic (unchanged from BLE integration)
    // ─────────────────────────────────────────────────────────────────

    private void updateBleButtonVisibility() {
        if (binding == null || binding.btnBleAction == null) return;

        binding.btnBleAction.setVisibility(View.GONE);
        if (binding.llActionButtons != null) binding.llActionButtons.setVisibility(View.GONE);
        if (binding.tvBleSenderName != null) binding.tvBleSenderName.setVisibility(View.GONE);

        if (isSender) {
            if (TaskStatus.PENDING.equals(currentTaskStatus) || TaskStatus.GOING_TO_PICKUP.equals(currentTaskStatus)) {
                if (binding.llActionButtons != null) binding.llActionButtons.setVisibility(View.VISIBLE);
            }
            if (TaskStatus.ARRIVED_PICKUP.equals(currentTaskStatus) || TaskStatus.WAITING_SENDER_LOAD.equals(currentTaskStatus)) {
                binding.btnBleAction.setVisibility(View.VISIBLE);
                binding.btnBleAction.setText("Package loaded into compartment");
                if (BleConstants.BLE_STATE_SENDER_LOADED.equals(currentBleState)) {
                    binding.btnBleAction.setEnabled(false);
                    binding.btnBleAction.setText("Waiting for robot confirmation...");
                } else {
                    binding.btnBleAction.setEnabled(true);
                }
            } else {
                binding.btnBleAction.setVisibility(View.GONE);
            }
        } else {
            if (TaskStatus.ARRIVED_DROPOFF.equals(currentTaskStatus) || TaskStatus.WAITING_RECEIVER_UNLOCK.equals(currentTaskStatus)) {
                binding.btnBleAction.setVisibility(View.VISIBLE);
                
                if (binding.tvBleSenderName != null) {
                    binding.tvBleSenderName.setVisibility(View.VISIBLE);
                    binding.tvBleSenderName.setText("Order from: " + currentSenderName);
                }
                if (BleConstants.BLE_STATE_RECEIVER_UNLOCKED.equals(currentBleState)) {
                    binding.btnBleAction.setText("Unlocking...");
                    binding.btnBleAction.setEnabled(false);
                } else {
                    binding.btnBleAction.setText("Tap to scan and unlock");
                    binding.btnBleAction.setEnabled(true);
                }
            } else {
                binding.btnBleAction.setVisibility(View.GONE);
                if (binding.tvBleSenderName != null) binding.tvBleSenderName.setVisibility(View.GONE);
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void handleBleAction() {
        if (!BlePermissionHelper.hasBlePermissions(this)) {
            BlePermissionHelper.requestBlePermissions(this);
            return;
        }

        if (!bleManager.isBluetoothEnabled()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Enable Bluetooth")
                .setMessage("You need to enable Bluetooth to connect to the robot. Do you want to enable it now?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Permission already checked by BlePermissionHelper above
                    android.content.Intent enableBtIntent = new android.content.Intent(
                            android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(enableBtIntent);
                })
                .setNegativeButton("No", (dialog, which) ->
                    android.widget.Toast.makeText(TrackingActivity.this,
                            "Cannot connect to robot without Bluetooth",
                            android.widget.Toast.LENGTH_LONG).show())
                .show();
            return;
        }

        if (currentBleToken == null) {
            Toast.makeText(this, "Missing BLE Token information", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnBleAction.setEnabled(false);
        String action;
        if (isSender) {
            action = BleConstants.ACTION_CONFIRM_LOADED;
        } else {
            action = BleConstants.ACTION_OPEN_SLOT; // Bỏ qua bước verify 2 lớp, gửi luôn lệnh mở khoang
        }

        Toast.makeText(this, "Connecting to robot via Bluetooth...", Toast.LENGTH_SHORT).show();

        bleManager.executeBleAction(currentRobotId, action, currentOrderId, currentBleToken, new BleActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(TrackingActivity.this, "Compartment unlocked successfully!", Toast.LENGTH_SHORT).show();
                    String newBleState = isSender ? BleConstants.BLE_STATE_SENDER_LOADED : BleConstants.BLE_STATE_RECEIVER_UNLOCKED;
                    if (taskRef != null) {
                        taskRef.child("bleState").setValue(newBleState);
                        // MOCK PI: Simulate state for UI test
                        taskRef.child("status").setValue(isSender ? TaskStatus.PICKED_UP : TaskStatus.DELIVERED);
                    }
                    if (!isSender) {
                        isReceiverTokenVerified = true;
                        updateBleButtonVisibility();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    binding.btnBleAction.setEnabled(true);
                    Toast.makeText(TrackingActivity.this, "BLE Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void handleCancelOrder() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes", (dialog, which) -> {
                if (taskRef != null) {
                    taskRef.child("status").setValue(TaskStatus.CANCELLED)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Order cancelled.", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to cancel: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                
                // ALSO cancel in the other user's node to keep them in sync
                if (isSender && currentReceiverUid != null && !currentReceiverUid.isEmpty()) {
                    FirebaseDatabase.getInstance(Constants.DB_URL)
                        .getReference("recipientTasks").child(currentReceiverUid).child(currentOrderId)
                        .child("status").setValue(TaskStatus.CANCELLED);
                } else if (!isSender && currentSenderUid != null && !currentSenderUid.isEmpty()) {
                    FirebaseDatabase.getInstance(Constants.DB_URL)
                        .getReference("tasks").child(currentSenderUid).child(currentOrderId)
                        .child("status").setValue(TaskStatus.CANCELLED);
                }
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void handleEditReceiver() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setHint("Enter new receiver phone number");
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = 50;
        params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);

        new android.app.AlertDialog.Builder(this)
            .setTitle("Edit Receiver Phone")
            .setView(container)
            .setPositiveButton("Save", (dialog, which) -> {
                String newPhone = input.getText().toString().trim();
                if (!newPhone.isEmpty() && taskRef != null) {
                    taskRef.child("receiverPhone").setValue(newPhone)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Receiver phone updated.", Toast.LENGTH_SHORT).show());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BlePermissionHelper.REQUEST_CODE_BLE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                handleBleAction();
            } else {
                Toast.makeText(this, "You need to grant Bluetooth and Location permissions to unlock!", Toast.LENGTH_LONG).show();
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

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

        mapStyle = null;
        routeGeoJson = null;
        deliveryRouteGeoJson = null;
        cachedMap = null;
        if (binding != null) binding.mapView.onDestroy();
        binding = null;

        if (taskRef != null && taskListener != null) {
            taskRef.removeEventListener(taskListener);
        }

        // MQTT removed
    }



}
