package com.example.autodeliveryapp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 *
 */
public final class Constants {


    private Constants() {
    }

    /**
     *
     *
     */
    public static final String DB_URL = BuildConfig.DB_URL;

    /**
     * API Key free from MapTiler Cloud (https:
     *
     */
    public static final String MAPTILER_API_KEY = BuildConfig.MAPTILER_API_KEY;

    /**
     *
     *
     */
    public static final String MAP_STYLE_URL = "https://api.maptiler.com/maps/streets-v2/style.json?key="
            + MAPTILER_API_KEY;

    /**
     *
     *
     */
    public static final String FCM_PREFS = "fcm_prefs";

    /** Key store temp FCM token in SharedPreferences */
    public static final String FCM_PREFS_KEY_PENDING_TOKEN = "pending_token";

    /**
     *
     *
     *
     *
     */
    public static final Map<String, double[]> DA_NANG_LOCATIONS;

    static {
        Map<String, double[]> locations = new LinkedHashMap<>();
        
        locations.put("dragon bridge", new double[]{16.0611, 108.2275});
        locations.put("cau rong", new double[]{16.0611, 108.2275});
        locations.put("airport", new double[]{16.0437, 108.1994});
        locations.put("san bay", new double[]{16.0437, 108.1994});
        locations.put("marble mountains", new double[]{15.9727, 108.2698});
        locations.put("ngu hanh son", new double[]{15.9727, 108.2698});
        locations.put("marble", new double[]{15.9727, 108.2698});
        locations.put("my khe beach", new double[]{16.0535, 108.2472});
        locations.put("my khe", new double[]{16.0535, 108.2472});
        locations.put("ba na", new double[]{15.9979, 107.9893});
        locations.put("bana hills", new double[]{15.9979, 107.9893});
        locations.put("hoi an", new double[]{15.8801, 108.3380});
        locations.put("tran cao van", new double[]{16.0691, 108.2045});
        locations.put("trần cao vân", new double[]{16.0691, 108.2045});
        locations.put("nguyen van linh", new double[]{16.0610, 108.2160});
        locations.put("nguyễn văn linh", new double[]{16.0610, 108.2160});
        locations.put("le duan", new double[]{16.0680, 108.2150});
        locations.put("lê duẩn", new double[]{16.0680, 108.2150});
        locations.put("pham van dong", new double[]{16.0690, 108.2350});
        locations.put("phạm văn đồng", new double[]{16.0690, 108.2350});
        locations.put("dien bien phu", new double[]{16.0640, 108.1960});
        locations.put("điện biên phủ", new double[]{16.0640, 108.1960});
        locations.put("quang trung", new double[]{16.0740, 108.2190});
        locations.put("hung vuong", new double[]{16.0670, 108.2170});
        locations.put("hùng vương", new double[]{16.0670, 108.2170});
        locations.put("phan chau trinh", new double[]{16.0610, 108.2205});
        locations.put("phan châu trinh", new double[]{16.0610, 108.2205});
        locations.put("bach dang", new double[]{16.0695, 108.2255});
        locations.put("bạch đằng", new double[]{16.0695, 108.2255});
        locations.put("tran phu", new double[]{16.0695, 108.2240});
        locations.put("trần phú", new double[]{16.0695, 108.2240});

        DA_NANG_LOCATIONS = Collections.unmodifiableMap(locations);
    }
}
