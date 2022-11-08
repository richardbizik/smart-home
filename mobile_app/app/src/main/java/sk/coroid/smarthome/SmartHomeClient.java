package sk.coroid.smarthome;

import android.content.Context;
import android.util.JsonReader;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import sk.coroid.smarthome.dto.DHTDataDTO;
import sk.coroid.smarthome.dto.GarageInfo;


public class SmartHomeClient {

    Context context;

    public SmartHomeClient(Context context) {
        this.context = context;
    }

    public void getGarageInfo(Function<GarageInfo, Void> function){
        RequestQueue requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
        requestQueue.start();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, Secret.SMART_HOME_SERVER_URL_MOBILE_INFO, null,
                response -> {
                    try {
                        GarageInfo data = new GarageInfo();
                        data.setPresence(response.getBoolean("presence"));
                        data.setDistance(response.getInt("distance"));
                        function.apply(data);
                    } catch (Exception e) {
                        Log.e("Failed parsing response", e.getMessage(), e);
                    }
                },
                error -> {
                    Log.e("Request", error.getMessage(), error);
                    function.apply(null);
                }
        ) {
            @Override
            public byte[] getBody() {
                return new byte[]{};
            }

            @Override
            public String getBodyContentType() {
                return "text/plain";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return SmartHomeClient.this.getHeaders();
            }
        };
        requestQueue.add(jsonObjectRequest);

    }

    public void getDHTData(Function<List<DHTDataDTO>, Void> function, String[] sensors) {
        RequestQueue requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
//        RequestQueue requestQueue = new RequestQueue(new NoCache(), getNetwork());
        requestQueue.start();

        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(Request.Method.GET, Secret.SMART_HOME_SERVER_URL_SENSOR + "?sensorNames="+String.join("&sensorNames=",sensors), null,
                response -> {
                    try {
                        List<DHTDataDTO> data = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject jsonObject = response.getJSONObject(i);
                            DHTDataDTO dto = new DHTDataDTO();
                            dto.setBatt(jsonObject.getDouble("batt"));
                            dto.setDate(OffsetDateTime.parse(jsonObject.getString("date")));
                            dto.setHum(jsonObject.getDouble("hum"));
                            dto.setSensorName(jsonObject.getString("sensorName"));
                            dto.setTemp(jsonObject.getDouble("temp"));
                            data.add(dto);
                        }
                        function.apply(data);
                    } catch (Exception e) {
                        Log.e("Failed parsing response", e.getMessage(), e);
                    }
                },
                error -> {
                    Log.e("Request", error.getMessage(), error);
                    function.apply(new ArrayList<>());
                }
        ) {
            @Override
            public byte[] getBody() {
                return new byte[]{};
            }

            @Override
            public String getBodyContentType() {
                return "text/plain";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return SmartHomeClient.this.getHeaders();
            }
        };
        requestQueue.add(jsonObjectRequest);
    }

    public void getDHTData(Function<List<List<DHTDataDTO>>, Void> function, int size, int page, String[] sensors) {
        RequestQueue requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
//        RequestQueue requestQueue = new RequestQueue(new NoCache(), getNetwork());
        requestQueue.start();

        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(Request.Method.GET, Secret.SMART_HOME_SERVER_URL_SENSOR + "more?sensorNames="+String.join("&sensorNames=",sensors)+"&size="+size+"&page="+page+"&sort=id,desc", null,
                response -> {
                    try {
                        List<List<DHTDataDTO>> data = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            List<DHTDataDTO> sensorData = new ArrayList<>();
                            JSONArray jsonArray = response.getJSONArray(i);
                            for (int j = 0; j < jsonArray.length(); j++) {
                                JSONObject jsonObject = jsonArray.getJSONObject(j);
                                DHTDataDTO dto = new DHTDataDTO();
                                dto.setBatt(jsonObject.isNull("batt")?null:jsonObject.getDouble("batt"));
                                dto.setDate(jsonObject.isNull("date")?null:OffsetDateTime.parse(jsonObject.getString("date")));
                                dto.setHum(jsonObject.isNull("hum")?null:jsonObject.getDouble("hum"));
                                dto.setSensorName(jsonObject.getString("sensorName"));
                                dto.setTemp(jsonObject.isNull("temp")?null:jsonObject.getDouble("temp"));
                                sensorData.add(dto);
                            }
                            data.add(sensorData);
                        }
                        function.apply(data);
                    } catch (Exception e) {
                        Log.e("Failed parsing response", e.getMessage(), e);
                    }
                },
                error -> {
                    Log.e("Request", error.getMessage(), error);
                    function.apply(new ArrayList<>());
                }
        ) {
            @Override
            public byte[] getBody() {
                return new byte[]{};
            }

            @Override
            public String getBodyContentType() {
                return "text/plain";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return SmartHomeClient.this.getHeaders();
            }
        };
        requestQueue.add(jsonObjectRequest);
    }

    private Map<String, String> getHeaders() {
        return getRequestHeaders();
    }

    public void sendCommand(String command, Function<Boolean, Void> function) {
        RequestQueue requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
        requestQueue.start();

        StringRequest stringRequest = new StringRequest(Request.Method.POST, Secret.SMART_HOME_SERVER_URL_COMMAND,
                response -> function.apply(Boolean.TRUE),
                error -> {
                    Log.e("Request", error.getMessage(), error);
                    function.apply(Boolean.FALSE);
                }
        ) {
            @Override
            public byte[] getBody() {
                return command.getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "text/plain";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getRequestHeaders();
            }
        };

        requestQueue.add(stringRequest);
    }

    private Map<String, String> getRequestHeaders() {
        Map<String, String> headers = new HashMap<>();
        String s = Secret.USER + ":" + Secret.PASSWORD;
        headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(s.getBytes()));
        return headers;
    }
}
