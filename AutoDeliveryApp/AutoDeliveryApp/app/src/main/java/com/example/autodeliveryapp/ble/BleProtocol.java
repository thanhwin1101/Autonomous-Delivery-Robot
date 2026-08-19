package com.example.autodeliveryapp.ble;

import org.json.JSONException;
import org.json.JSONObject;

public class BleProtocol {
    
    public static String buildRequest(String action, String taskId, String token) {
        try {
            JSONObject json = new JSONObject();
            json.put("action", action);
            json.put("taskId", taskId);
            json.put("token", token);
            json.put("timestamp", System.currentTimeMillis());
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    public static class Response {
        public boolean ok;
        public String action;
        public String error;
        
        public static Response parse(String jsonString) {
            Response resp = new Response();
            try {
                JSONObject json = new JSONObject(jsonString);
                resp.ok = json.optBoolean("ok", false);
                resp.action = json.optString("action", "");
                resp.error = json.optString("error", "");
            } catch (JSONException e) {
                resp.ok = false;
                resp.error = "INVALID_RESPONSE";
            }
            return resp;
        }
    }
}
