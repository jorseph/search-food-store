package com.example.currentplacedetailsonmap;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.currentplacedetailsonmap.data.LocationInfo;
import com.example.currentplacedetailsonmap.data.MarkerHelper;
import com.example.currentplacedetailsonmap.util.ConfigUtil;
import com.example.currentplacedetailsonmap.util.GsonUtil;
import com.example.currentplacedetailsonmap.util.PlaceJSONParser;
import com.example.currentplacedetailsonmap.adapter.MapInfoAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

//import static android.R.attr.radius;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class MapsActivityCurrentPlace extends AppCompatActivity
        implements OnMapReadyCallback,
                GoogleApiClient.ConnectionCallbacks,
                GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MapsActivityCurrentPlace.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // The entry point to Google Play services, used by the Places API and Fused Location Provider.
    private GoogleApiClient mGoogleApiClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(25.12, 121.50);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;
    private final int radius = 500;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Used for selecting the current place.
    private final int mMaxEntries = 20;
    private String[] mLikelyPlaceNames;
    private String[] mLikelyPlaceAddresses;
    private String[] mLikelyPlaceAttributions;
    private String[] mLikelyPlaceType;
    private LatLng[] mLikelyPlaceLatLngs;

    //private LatLng markerLatLng[];
    //private String mTargetPlcaeName[];
    private PlaceLikelihood[] target_food_place;

    private double mLatitude = mDefaultLocation.latitude;
    private double mLongitude = mDefaultLocation.longitude;

    private GroundOverlay imageOverlay;
    private Spinner mSprPlaceType;
    private MapInfoAdapter mapInfoAdapter;

    private Button btn_lbs_find; // 下拉選單搜尋
    private EditText et_lbs_keyword;
    private Button btn_lbs_searchKeyword; // 關鍵字搜尋
    private SupportMapFragment frg_lbs_map;
    private ImageButton imgbtn_lbs_getLocation;  //share location

    private String[] mPlaceType = null;
    private String[] mPlaceTypeName = null;

    public static final int LOCATION_UPDATE_MIN_DISTANCE = 10;
    public static final int LOCATION_UPDATE_MIN_TIME = 5000;

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                //Log.d(String.format("%f, %f", location.getLatitude(), location.getLongitude()));
                drawMarker(location);
                mLocationManager.removeUpdates(mLocationListener);
            } else {
                //Logger.d("Location is null");
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };
    private LocationManager mLocationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_main);
        initComponent();
        initServerData();
        // Build the Play services client for use by the Fused Location Provider and the Places API.
        // Use the addApi() method to request the Google Places API and the Fused Location Provider.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();

        // 搜尋
        btn_lbs_find.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                et_lbs_keyword.setText("");
                int selectedPosition = mSprPlaceType.getSelectedItemPosition();
                StringBuilder sb = null;
                if (selectedPosition == 0) {
                    // 自行車道
                    searchKeyword("自行車道");
                } else {
                    String type = mPlaceType[selectedPosition];
                    sb = new StringBuilder(ConfigUtil.GOOGLE_SEARCH_API);
                    sb.append("location=" + mLatitude + "," + mLongitude);
                    sb.append("&radius=" + radius);
                    sb.append("&types=" + type);
                    sb.append("&sensor=true");
                    sb.append("&key=" + ConfigUtil.API_KEY_GOOGLE_MAP);
                    PlacesTask placesTask = new PlacesTask(MapsActivityCurrentPlace.this);
                    placesTask.execute(sb.toString());
                }
            }
        });

        // 關鍵字搜尋
        btn_lbs_searchKeyword.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // 關閉鍵盤
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(et_lbs_keyword.getWindowToken(), 0); // et_setting_name為獲取焦點的EditText
                // 搜尋關鍵字
                String keyword = et_lbs_keyword.getText().toString();
                if (!TextUtils.isEmpty(keyword)) {
                    searchKeyword(keyword);
                } else {
                    Toast.makeText(MapsActivityCurrentPlace.this,
                            getString(R.string.noKeyword), Toast.LENGTH_LONG)
                            .show();
                }
            }
        });

        //share location
        imgbtn_lbs_getLocation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //取得map中心點座標
                Log.i(ConfigUtil.TAG, "中心點:" + mMap.getProjection().fromScreenLocation(new Point(frg_lbs_map.getView().getWidth()/2, frg_lbs_map.getView().getHeight()/2)));
            }
        });

    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Builds the map when the Google Play services client is successfully connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frg_lbs_map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Handles failure to connect to the Google Play services client.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        // Refer to the reference doc for ConnectionResult to see what error codes might
        // be returned in onConnectionFailed.
        Log.d(TAG, "Play services connection failed: ConnectionResult.getErrorCode() = "
                + result.getErrorCode());
    }

    /**
     * Handles suspension of the connection to the Google Play services client.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Play services connection suspended");
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */
    //@Override
    //public boolean onOptionsItemSelected(MenuItem item) {
    //    if (item.getItemId() == R.id.btn_lbs_searchKeyword) {
    //        showCurrentPlace();
    //    }
    //    return true;
    //}

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout)findViewById(R.id.map), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        //地圖上 marker 點擊後彈出的畫面
        mapInfoAdapter = new MapInfoAdapter(MapsActivityCurrentPlace.this, false);
        mMap.setInfoWindowAdapter(mapInfoAdapter);

        /*
        //準備預設要顯示的資料
        List<LocationInfo> locationInfoList = new ArrayList<LocationInfo>();
        //公司
        LocationInfo locationInfo1 = new LocationInfo("24.1651456", "120.66150479999999", "台中市西屯區漢口路二段151號13樓之9", "0423165803", "麥司奇科技股份有限公司", 1, R.drawable.maxkit);
        locationInfoList.add(locationInfo1);
        //客戶
        LocationInfo locationInfo2 = new LocationInfo("25.0322124", "121.52718989999994", "台北市大安區金山南路2段55號", null, "郵政總局", 2, null);
        locationInfoList.add(locationInfo2);
        LocationInfo locationInfo3 = new LocationInfo("25.078345", "121.56994299999997", "台北市11492內湖區瑞光路468號", null, "遠傳電信", 2, null);
        locationInfoList.add(locationInfo3);
        LocationInfo locationInfo4 = new LocationInfo("24.18106", "120.62026200000003", "臺中市西屯區台灣大道四段798號", "0800-021818", "台灣櫻花", 2, null);
        locationInfoList.add(locationInfo4);
        //自行車道
        LocationInfo locationInfo5 = new LocationInfo("24.136734", "120.69739600000003", "台中市東區東光園路446之1號", null, "東光園道自行車道", 3, R.drawable.bicycle_road1);
        locationInfoList.add(locationInfo5);
        LocationInfo locationInfo6 = new LocationInfo("24.2327708", "120.69442279999998", "台中市神岡區潭雅神綠園道", null, "潭雅神綠園道", 3, R.drawable.bicycle_road2);
        locationInfoList.add(locationInfo6);
        LocationInfo locationInfo7 = new LocationInfo("24.1734898", "120.70740120000005", "台中市北屯區旱溪西路三段", null, "旱溪「親水式」自行車道", 3, R.drawable.bicycle_road3);
        locationInfoList.add(locationInfo7);

        parseLocationAndShowMap(locationInfoList);
        */
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        if (mLocationPermissionGranted) {
            mLastKnownLocation = LocationServices.FusedLocationApi
                    .getLastLocation(mGoogleApiClient);
        }

        // Set the map's camera position to the current location of the device.
        if (mCameraPosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
        } else if (mLastKnownLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(mLastKnownLocation.getLatitude(),
                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
        } else {
            Log.d(TAG, "Current location is null. Using defaults.");
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    private void showCurrentPlace() {
        if (mMap == null) {
            return;
        }

        if (mLocationPermissionGranted) {
            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            @SuppressWarnings("MissingPermission")
            PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi
                    .getCurrentPlace(mGoogleApiClient, null);
            result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
                @Override
                public void onResult(@NonNull PlaceLikelihoodBuffer likelyPlaces) {
                    int i = 0;
                    int j = 0;
                    String temp_type = "";
                    for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                        temp_type = placeLikelihood.getPlace().getPlaceTypes().toString();
                        if(checkType(temp_type,"38") || checkType(temp_type,"34]")) {
                            //mTargetPlcaeName[j] = temp_type;
                            //markerLatLng = placeLikelihood.getPlace().getLatLng();
                            j++;
                         }
                        i++;
                        if (i > (mMaxEntries - 1)) {
                            break;
                        }
                    }

                    if(j > 0) {
                        mLikelyPlaceNames = new String[j];
                        mLikelyPlaceAddresses = new String[j];
                        mLikelyPlaceAttributions = new String[j];
                        mLikelyPlaceType = new String[j];
                        mLikelyPlaceLatLngs = new LatLng[j];
                        i = 0;
                        j = 0;
                        for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                            temp_type = placeLikelihood.getPlace().getPlaceTypes().toString();
                            if(checkType(temp_type,"38") || checkType(temp_type,"34]")) {
                                mLikelyPlaceNames[j] = (String) placeLikelihood.getPlace().getName();
                                mLikelyPlaceAddresses[j] = (String) placeLikelihood.getPlace().getAddress();
                                mLikelyPlaceAttributions[j] = (String) placeLikelihood.getPlace()
                                        .getAttributions();
                                mLikelyPlaceLatLngs[j] = placeLikelihood.getPlace().getLatLng();
                                Log.v(TAG,"mLikelyPlaceNames = " + mLikelyPlaceNames[j]);
                                Log.v(TAG,"mLikelyPlaceAddresses = " + mLikelyPlaceAddresses[j]);
                                Log.v(TAG,"mLikelyPlaceAttributions = " + mLikelyPlaceAttributions[j]);
                                Log.v(TAG,"mLikelyPlaceLatLngs = " + mLikelyPlaceLatLngs[j]);
                                j++;
                            }
                            i++;
                            if (i > (mMaxEntries - 1)) {
                                break;
                            }
                        }

                    }
                    // Release the place likelihood buffer, to avoid memory leaks.
                    likelyPlaces.release();

                    // Show a dialog offering the user the list of likely places, and add a
                    // marker at the selected place.
                    //openPlacesDialog();
                }
            });
        } else {
            // Add a default marker, because the user hasn't selected a place.
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet)));
        }
    }

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    private void openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The "which" argument contains the position of the selected item.
                        LatLng markerLatLng = mLikelyPlaceLatLngs[which];
                        String markerSnippet = mLikelyPlaceAddresses[which];
                        if (mLikelyPlaceAttributions[which] != null) {
                            markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[which];
                        }
                        // Add a marker for the selected place, with an info window
                        // showing information about that place.
                        mMap.addMarker(new MarkerOptions()
                                .title(mLikelyPlaceNames[which])
                                .position(markerLatLng)
                                .snippet(markerSnippet));
                        // Position the map's camera at the location of the marker.
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                                DEFAULT_ZOOM));
                    }
                };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_place)
                .setItems(mLikelyPlaceNames, listener)
                .show();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        //mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mLastKnownLocation = null;
        }

        //getCurrentLocation();
    }

    private boolean checkType(String type, String targetType) {
        Log.v(TAG,"type = " + type + "targetType = " + targetType);
        //String str = Arrays.asList(type.split(", "));
        List<String> arrList = Arrays.asList(type.split(", "));
        if(arrList.contains(targetType)) {
            Log.v(TAG,"true");
            return true;
        } else {
            Log.v(TAG,"true");
            return false;
        }
    }

    /**
     * 用關鍵字搜尋地標
     *
     * @param keyword
     */
    private void searchKeyword(String keyword) {
        try {
            String unitStr = URLEncoder.encode(keyword, "utf8");  //字體要utf8編碼
            StringBuilder sb = new StringBuilder(ConfigUtil.GOOGLE_SEARCH_API);
            sb.append("location=" + mLatitude + "," + mLongitude);
            sb.append("&radius=" + radius);
            //sb.append("&keyword=" + unitStr);
            sb.append("&sensor=true");
            sb.append("&key=" + ConfigUtil.API_KEY_GOOGLE_MAP);  //server key
            PlacesTask placesTask = new PlacesTask(MapsActivityCurrentPlace.this);
            Log.v(TAG, sb.toString());
            placesTask.execute(sb.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.i(ConfigUtil.TAG, "Exception:" + e);
        }
    }

    /** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    /** A class, to download Google Places */
    private class PlacesTask extends AsyncTask<String, Integer, String> {

        private MapsActivityCurrentPlace context = null;
        String data = null;

        public PlacesTask(MapsActivityCurrentPlace context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... url) {
            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //context.dialog = ProgressDialog.show(context, "",
            //       context.getString(R.string.loading), true);
            //context.openPlacesDialog();
        }

        @Override
        protected void onPostExecute(String result) {
            //context.dialog.dismiss();
            //context.dismissDialog(2);
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);
        }
    }

    /** A class to parse the Google Places in JSON format */
    private class ParserTask extends
            AsyncTask<String, Integer, List<HashMap<String, String>>> {

        JSONObject jObject;

        @Override
        protected List<HashMap<String, String>> doInBackground(
                String... jsonData) {

            List<HashMap<String, String>> places = null;
            PlaceJSONParser placeJsonParser = new PlaceJSONParser();

            try {
                jObject = new JSONObject(jsonData[0]);
                Log.v(TAG,jObject.toString());
                places = placeJsonParser.parse(jObject);

            } catch (Exception e) {
                Log.d("Exception", e.toString());
            }
            return places;
        }

        @Override
        protected void onPostExecute(List<HashMap<String, String>> list) {

            // Clears all the existing markers
            mMap.clear();
            mapInfoAdapter.setKeyword(true);
            for (int i = 0; i < list.size(); i++) {
                MarkerOptions markerOptions = new MarkerOptions();
                HashMap<String, String> hmPlace = list.get(i);
                double lat = Double.parseDouble(hmPlace.get("lat"));
                double lng = Double.parseDouble(hmPlace.get("lng"));
                LatLng latLng = new LatLng(lat, lng);
                markerOptions.position(latLng);
                String name = hmPlace.get("place_name");
                markerOptions.title(name);
                String vicinity = hmPlace.get("vicinity");
                MarkerHelper markerHelper = new MarkerHelper(name, vicinity);
                String snippet = GsonUtil.gson.toJson(markerHelper);
                markerOptions.snippet(snippet);
                mMap.addMarker(markerOptions);
            }
            //LatLng latLng = new LatLng(mLatitude, mLongitude);
            //addMyLocationIcon(latLng);
        }
    }

    /**
     * 在 map上增加自己的位置
     * @param latLng
     */
    private void addMyLocationIcon(LatLng latLng) {
        GroundOverlayOptions newarkMap = new GroundOverlayOptions().image(
                BitmapDescriptorFactory.fromResource(R.drawable.man1))
                .position(latLng, 94, 200);
        imageOverlay = mMap.addGroundOverlay(newarkMap);
    }

    public void onLocationChanged(Location location) {
        //location有異動才更新畫面
        if(mLatitude!=location.getLatitude() || mLongitude!=location.getLongitude()){
            mLatitude = location.getLatitude();
            mLongitude = location.getLongitude();
            LatLng latLng = new LatLng(mLatitude, mLongitude);

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));

            // 在map上加上圖示(黃色小人)
            if (imageOverlay != null) {
                imageOverlay.remove();
            }
            addMyLocationIcon(latLng);
        }
    }

    // 連結實體物件
    private void initComponent() {
        btn_lbs_find = (Button) findViewById(R.id.btn_lbs_find);
        et_lbs_keyword = (EditText) findViewById(R.id.et_lbs_keyword);
        btn_lbs_searchKeyword = (Button) findViewById(R.id.btn_lbs_searchKeyword); // 關鍵字搜尋
        frg_lbs_map = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frg_lbs_map);
        imgbtn_lbs_getLocation = (ImageButton) findViewById(R.id.imgbtn_lbs_getLocation);
    }

    private void initServerData() {
        mPlaceType = getResources().getStringArray(R.array.place_type);
        mPlaceTypeName = getResources().getStringArray(R.array.place_type_name);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, mPlaceTypeName);
        mSprPlaceType = (Spinner) findViewById(R.id.spr_place_type);
        mSprPlaceType.setAdapter(adapter);
/*
        //檢測 device 是否有安裝 google play services，且  google play services 版本是否符合需求
        int status = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(getBaseContext());

        if (status != ConnectionResult.SUCCESS) {
            int requestCode = 10;  //google map 最低 google play services 需求為 api 10
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this,
                    requestCode);
            dialog.show();
        } else {
            //mMap = frg_lbs_map.getMap();
            mMap.setMyLocationEnabled(true);  //顯示自己的位置
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                //如果GPS或網路定位開啟，更新位置
                Criteria criteria = new Criteria();
                String provider = locationManager.getBestProvider(criteria, true);  //取得定位裝置 ()
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    onLocationChanged(location);
                }
                //locationManager.requestLocationUpdates(provider, 50000, 0, this);

            } else {
                Toast.makeText(MapsActivityCurrentPlace.this, "請打開定位功能", Toast.LENGTH_LONG).show();
            }
        }

        //地圖上 marker 點擊後彈出的畫面
        mapInfoAdapter = new MapInfoAdapter(MapsActivityCurrentPlace.this, false);
        mMap.setInfoWindowAdapter(mapInfoAdapter);


        //準備預設要顯示的資料
        List<LocationInfo> locationInfoList = new ArrayList<LocationInfo>();
        //公司
        LocationInfo locationInfo1 = new LocationInfo("24.1651456", "120.66150479999999", "台中市西屯區漢口路二段151號13樓之9", "0423165803", "麥司奇科技股份有限公司", 1, R.drawable.maxkit);
        locationInfoList.add(locationInfo1);
        //客戶
        LocationInfo locationInfo2 = new LocationInfo("25.0322124", "121.52718989999994", "台北市大安區金山南路2段55號", null, "郵政總局", 2, null);
        locationInfoList.add(locationInfo2);
        LocationInfo locationInfo3 = new LocationInfo("25.078345", "121.56994299999997", "台北市11492內湖區瑞光路468號", null, "遠傳電信", 2, null);
        locationInfoList.add(locationInfo3);
        LocationInfo locationInfo4 = new LocationInfo("24.18106", "120.62026200000003", "臺中市西屯區台灣大道四段798號", "0800-021818", "台灣櫻花", 2, null);
        locationInfoList.add(locationInfo4);
        //自行車道
        LocationInfo locationInfo5 = new LocationInfo("24.136734", "120.69739600000003", "台中市東區東光園路446之1號", null, "東光園道自行車道", 3, R.drawable.bicycle_road1);
        locationInfoList.add(locationInfo5);
        LocationInfo locationInfo6 = new LocationInfo("24.2327708", "120.69442279999998", "台中市神岡區潭雅神綠園道", null, "潭雅神綠園道", 3, R.drawable.bicycle_road2);
        locationInfoList.add(locationInfo6);
        LocationInfo locationInfo7 = new LocationInfo("24.1734898", "120.70740120000005", "台中市北屯區旱溪西路三段", null, "旱溪「親水式」自行車道", 3, R.drawable.bicycle_road3);
        locationInfoList.add(locationInfo7);

        parseLocationAndShowMap(locationInfoList);*/
    }

    /**
     * 在map上顯示門市句點的位置
     *
     * @param locationInfoList
     */
    public void parseLocationAndShowMap(List<LocationInfo> locationInfoList) {
        mMap.clear();
        mapInfoAdapter.setKeyword(false);

        // 標注據點位置
        for (LocationInfo locationInfo : locationInfoList) {
            MarkerOptions markerOptions = new MarkerOptions();
            double lat = Double.parseDouble(locationInfo.getLat());
            double lng = Double.parseDouble(locationInfo.getLng());
            LatLng latLng = new LatLng(lat, lng);
            markerOptions.position(latLng);

            String name = locationInfo.getName();
            int icon = R.drawable.location;
            switch (locationInfo.getAtype()) {
                case 1:
                    // 公司
                    icon = R.drawable.maxkit;
                    break;
                case 2:
                    // 客戶
                    icon = R.drawable.custom;
                    break;
                case 3:
                    // 自行車道
                    icon = R.drawable.bicycle;
                    break;
                default:
                    icon = R.drawable.location;
                    break;
            }
            markerOptions.title(name); // map上icon點擊後顯示資料
            markerOptions.icon(BitmapDescriptorFactory.fromResource(icon)); // 設定map上顯示的圖示

            MarkerHelper markerHelper = new MarkerHelper(locationInfo);
            String snippet = GsonUtil.gson.toJson(markerHelper);
            markerOptions.snippet(snippet);
            mMap.addMarker(markerOptions);
        }
        LatLng latLng = new LatLng(mLatitude, mLongitude);
        addMyLocationIcon(latLng);
    }

    private void getCurrentLocation() {
        boolean isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        Location location = null;
        if (!(isGPSEnabled || isNetworkEnabled)){}
            //Snackbar.make(mMapView, R.string.error_location_provider, Snackbar.LENGTH_INDEFINITE).show();
        else {
            if (isNetworkEnabled) {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        LOCATION_UPDATE_MIN_TIME, LOCATION_UPDATE_MIN_DISTANCE, mLocationListener);
                location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (isGPSEnabled) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATE_MIN_TIME, LOCATION_UPDATE_MIN_DISTANCE, mLocationListener);
                location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        }
        if (location != null) {
            Log.d(TAG, "" + location.getLatitude() + " " + location.getLongitude());
            drawMarker(location);
        }
    }

    private void drawMarker(Location location) {
        if (mMap != null) {
            mMap.clear();
            LatLng gps = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.addMarker(new MarkerOptions()
                    .position(gps)
                    .title("Current Position"));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gps, 12));
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //getCurrentLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //mLocationManager.removeUpdates(mLocationListener);
    }
}
