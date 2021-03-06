package com.sada.blight;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback, LoaderManager.LoaderCallbacks<String> {

    private static final String TAG = "HomeActivity";
    private static final int LOADER_ID = 2;
    private FirebaseAuth mAuth;
    private GoogleMap mMap;
    private String location;
    private double lat;
    private double lon;
    private String QUERY_URL;
    private String BASE_URL = "https://blight-backend.herokuapp.com/query?location=";
    private boolean alreadyLoaded = false;

    private TextView tvTemp, tvPressure, tvVisibility, tvWind, tvHumidity;
    private LinearLayout dataContainer;
    private boolean showAlert = false;
    private LinearLayout alertContainer;
    private Animation blinkAnimation;
    private String alertTitle;
    private String alertMessage;
    private Button bHelpMe;
    private View snackbarView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViews();

        showAlert = getIntent().getBooleanExtra("showAlert", false);
        if (showAlert) {
            operationAlert();
        }
        ((ImageButton) findViewById(R.id.bLogout)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAuth.signOut();
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                finish();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Bundle locationDetails = fetchLocation();

        bHelpMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbarView = getCurrentFocus();
                final Bundle locationDetails = fetchLocation();
                DatabaseReference currentUser = FirebaseDatabase.getInstance().getReference().child("users")
                        .child(mAuth.getCurrentUser().getUid());
                currentUser.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChild("contact")) {
                            String contact = dataSnapshot.child("contact").getValue().toString();
                            DatabaseReference alertRef = FirebaseDatabase.getInstance().getReference().child("alerts");

                            HashMap<String, String> alertMap = new HashMap<String, String>();
                            alertMap.put("location", locationDetails.getString("location"));
                            alertMap.put("lat", locationDetails.getString("lat"));
                            alertMap.put("lon", locationDetails.getString("lon"));
                            alertMap.put("contact", contact);

                            alertRef.child(mAuth.getCurrentUser().getUid()).setValue(alertMap)
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Snackbar.make(findViewById(R.id.bHelpMe),
                                                    "Alert has been sent to the Authority, Stay put we will be there soon",
                                                    Snackbar.LENGTH_LONG).show();
                                            bHelpMe.setText("Approaching you");
                                            bHelpMe.setClickable(false);
                                            bHelpMe.setTextSize(20f);
                                            Drawable img = getResources().getDrawable( R.drawable.ic_approching );
                                            bHelpMe.setCompoundDrawablesWithIntrinsicBounds( img, null, null, null);
                                            bHelpMe.setBackground(getResources().getDrawable(R.drawable.bg_approaching));
                                            bHelpMe.setTextColor(Color.rgb(20,150,80));
                                            Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim
                                                    .blink);
                                            bHelpMe.startAnimation(animation);

                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        });
    }

    private Bundle fetchLocation() {
        Bundle locationDetails = new Bundle();
        GPSTracker gps = new GPSTracker(HomeActivity.this);
        if (gps.canGetLocation()) {
            String output = "";
            lat = gps.getLatitude();
            lon = gps.getLongitude();
            locationDetails.putString("lat", lat + "");
            locationDetails.putString("lon", lon + "");
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            location = "";
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                Address obj = addresses.get(0);//.getAddressLine(0);
                location = obj.getLocality();
                locationDetails.putString("location", location);
                QUERY_URL = BASE_URL + location;
                initiateLoader();
                Toast.makeText(getApplicationContext(), location, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
                intent.putExtra("lat", lat);
                intent.putExtra("lon", lon);
                intent.putExtra("location", location);
//                startActivity(intent);
//                finish();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }
        return locationDetails;
    }

    private void operationAlert() {
        alertTitle = getIntent().getStringExtra("title");
        alertTitle = alertTitle.substring(5);
        alertMessage = getIntent().getStringExtra("message");
        ((TextView) findViewById(R.id.tvAlertTitle)).setText(alertTitle);
        ((TextView) findViewById(R.id.tvAlertMessage)).setText(alertMessage);

        alertContainer.setVisibility(View.VISIBLE);
        blinkAnimation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.blink);
        alertContainer.startAnimation(blinkAnimation);
    }

    private void findViews() {
        alertContainer = findViewById(R.id.alertContainer);
        tvTemp = findViewById(R.id.tvTemp);
        tvPressure = findViewById(R.id.tvPressure);
        tvWind = findViewById(R.id.tvWind);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvVisibility = findViewById(R.id.tvVisibility);
        dataContainer = findViewById(R.id.dataContainer);
        bHelpMe = findViewById(R.id.bHelpMe);
    }

    private void initiateLoader() {
        // Get a reference to the ConnectivityManager to check state of network connectivity
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get details on the currently active default data network
        NetworkInfo networkInfo = null;
        if (connMgr != null) {
            networkInfo = connMgr.getActiveNetworkInfo();
        }

        // If there is a network connection, fetch data
        if (networkInfo != null && networkInfo.isConnected()) {
            // Get a reference to the LoaderManager, in order to interact with loaders.

            LoaderManager loaderManager = getSupportLoaderManager();
            if (alreadyLoaded) {
                loaderManager.restartLoader(LOADER_ID, null, this);
            } else {
                loaderManager.initLoader(LOADER_ID, null, this);
                alreadyLoaded = true;
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Internet Connection available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {

        final List<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();

        DatabaseReference rescueWorkers = FirebaseDatabase.getInstance().getReference().child("rescueworkers");
        rescueWorkers.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
//                Map<String, HashMap<String, String>> td = (HashMap<String, HashMap<String, String>>) dataSnapshot.getValue();
//                List<HashMap<String, String>> objectList = (List<HashMap<String, String>>) td.values();
//                for (HashMap<String, String> obj : objectList) {
//                    String rescueLot = obj.get("lat");
//                    String rescueLon = obj.get("lon");
//                    String region = obj.get("region");
//                    if (region.equals(location)) {
//
//                    }
//                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        // Add a marker in Sydney and move the camera
        LatLng position = new LatLng(lat, lon);
        mMap.addMarker(new MarkerOptions().position(position).title("Marker in " + location));
        position = new LatLng(lat - 0.001d, lon - 0.001);
        mMap.addMarker(new MarkerOptions().position(position).title("Marker in " + location));
        position = new LatLng(lat + 0.003d, lon + 0.0015);
        mMap.addMarker(new MarkerOptions().position(position).title("Marker in " + location));
        position = new LatLng(lat - 0.004d, lon - 0.006);
        mMap.addMarker(new MarkerOptions().position(position).title("Marker in " + location));
        position = new LatLng(lat + 0.001d, lon + 0.003);
        mMap.addMarker(new MarkerOptions().position(position).title("Marker in " + location));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(position));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15.0f));
    }

    @Override
    public Loader<String> onCreateLoader(int id, Bundle args) {

        return new DataLoader(this, QUERY_URL);
    }

    @Override
    public void onLoadFinished(Loader<String> loader, String data) {
        String output = "Fetched data : " + data;
        Log.i(TAG, "onLoadFinished: " + output);
//        Toast.makeText(getApplicationContext(), output, Toast.LENGTH_SHORT).show();
        WeatherData weatherData = new WeatherData();
        weatherData = QueryUtils.extractFeatureFromJson(data);
//        Toast.makeText(getApplicationContext(), "" + weatherData.getForPD3(), Toast.LENGTH_SHORT).show();
        try {
            tvTemp.setText((weatherData.getTemp().toString().substring(0, 2)));
            tvPressure.setText(weatherData.getPressure().toString().substring(0, 4));
            tvHumidity.setText(weatherData.getHumidity().toString().substring(0, 3));
            tvVisibility.setText(weatherData.getVisibility().toString().substring(0, 3));
            tvWind.setText(weatherData.getWind().toString().substring(0, 3));
        } catch (Exception e) {
            e.printStackTrace();
        }
        dataContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {

    }
}
