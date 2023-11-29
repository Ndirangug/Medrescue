package com.jps.medrescue;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Text;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import static android.content.ContentValues.TAG;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private Polyline currentPolyline;


    private FusedLocationProviderClient mFusedLocationClient;
    ArrayList<Marker> hospitalMarkers = new ArrayList<>();
    boolean getDriversAroundStarted = false;
    List<Marker> markers = new ArrayList<Marker>();

    private Button mLogout, mRequest, mSettings;
    private LatLng pickupLocation;
    private Boolean requestBol = false;
    private Marker pickupMarker;

    private SupportMapFragment mapFragment;

    private String destination;

    private LatLng destinationLatlng;

    private RatingBar mRatingBar;

    private LinearLayout mDriverInfo;

    private TextView mDriverName, mDriverPhone, mDriverCar, mCustomerDestination;

    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;

    GeoQuery geoQuery;

    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);


        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), BuildConfig.MAPS_API_KEY, Locale.US);
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        destinationLatlng = new LatLng(0.0, 0.0);

        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);

        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhone = (TextView) findViewById(R.id.driverPhone);
        mDriverCar = (TextView) findViewById(R.id.driverCar);
        mCustomerDestination = (TextView)  findViewById(R.id.customerDestination);

        mRequest = (Button) findViewById(R.id.request);
        mSettings = (Button) findViewById(R.id.settings);
        mRatingBar = (RatingBar) findViewById(R.id.ratingBar);
        mLogout = (Button) findViewById(R.id.logout);


        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, Welcome_Activity.class);
                startActivity(intent);
                finish();
                return;
            }

        });


        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestBol) {
                    endRide();
                } else {
                }

                requestBol = true;
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.patient)));

                mRequest.setText("Getting Your Ambulance...");

                getDriversAround();
            }
        });


        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }

        });


        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
                destinationLatlng = place.getLatLng();
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });

    }



    private void getDriverLocation() {
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversAvailable").child(driverFoundID).child("l");
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && requestBol) {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double LocationLat = 0;
                    double LocationLng = 0;
                    // mRequest.setText("Ambulance Found");
                    if (map.get(0) != null) {
                        LocationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) {
                        LocationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(LocationLat, LocationLng);
                    if (mDriverMarker != null) {
                        mDriverMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);
                    if (distance < 100) {
                        mRequest.setText("Ambulance Arrived");
                    } else {
                        int dis = (int) distance / 1000;
                        mRequest.setText("Ambulance Found: " + String.valueOf(dis) + " Kms away...");

                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your Ambulance").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ambulance)));
                    getDirections(driverLatLng);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }


    private void getDriverInfo() {
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    if (dataSnapshot.child("Name").getValue() != null) {
                        mDriverName.setText(dataSnapshot.child("Name").getValue().toString());
                    }
                    if (dataSnapshot.child("Phone").getValue() != null) {
                        mDriverPhone.setText(dataSnapshot.child("Phone").getValue().toString());
                    }
                    if (dataSnapshot.child("Car").getValue() != null) {
                        mDriverCar.setText(dataSnapshot.child("Car").getValue().toString());
                    }

                    int ratingSum = 0;
                    float ratingsTotal = 0;
                    float ratingsAvg = 0;
                    for (DataSnapshot child : dataSnapshot.child("rating").getChildren()) {
                        ratingSum = ratingSum + Integer.valueOf(child.getValue().toString());
                        ratingsTotal++;
                    }
                    if (ratingsTotal != 0) {
                        ratingsAvg = ratingSum / ratingsTotal;
                        mRatingBar.setRating(ratingsAvg);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            } else {
                checkLocationPermission();
            }
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);

        mFusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnCompleteListener((locationTask)->{
          Location location = locationTask.getResult();
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));
        });

        fetchHospitalsAround();

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if(marker.getTag() == "Hospital"){
                   destinationLatlng = marker.getPosition();
                   destination = marker.getTitle().replaceAll("_", " ");
                   mCustomerDestination.setText(destination);
                }

                return false;
            }
        });

    }



    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                mLastLocation = location;
            }
        }

    };



    private void getDriversAround() {
        getDriversAroundStarted = true;
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), radius);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {

            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestBol) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                    if (driverFound) {
                        return;
                    }

                    driverFound = true;
                    driverFoundID = key;

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerId);
                    map.put("destination", destination);
                    map.put("destinationLat", destinationLatlng.latitude);
                    map.put("destinationLng", destinationLatlng.longitude);
                    driverRef.updateChildren(map);

                    getDriverLocation();
                    getDriverInfo();
                    getHasRideEnded();
                    mRequest.setText("Looking for Driver's Location....");

                }
            }

            @Override
            public void onKeyExited(String key) {
                for (Marker markerIt : markers) {
                    if (markerIt.getTag().equals(key)) {
                        markerIt.remove();
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for (Marker markerIt : markers) {
                    if (markerIt.getTag().equals(key)) {
                        markerIt.setPosition(new LatLng(location.latitude, location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound && radius < 99999999) {
                    radius+=10;
                    // getClosestDriver();
                    getDriversAround();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Please give permission...")
                        .setMessage("Please give permission...")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

                            }
                        })
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission...", Toast.LENGTH_LONG).show();
                }
                break;
            }


        }
    }

    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;

    private void getHasRideEnded() {
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest").child("customerRideId");
        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {


                } else {

                    endRide();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void endRide() {

        requestBol = false;
        if (geoQuery != null) {
            geoQuery.removeAllListeners();
        }
        if (driverLocationRefListener != null && driveHasEndedRefListener != null) {
            driverLocationRef.removeEventListener(driverLocationRefListener);
            driveHasEndedRef.removeEventListener(driveHasEndedRefListener);
        }


        if (driverFoundID != null) {
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
            driverRef.removeValue();
            driverFoundID = null;

        }
        driverFound = false;
        radius = 1;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
        if (pickupMarker != null) {
            pickupMarker.remove();
        }
        if (mDriverMarker != null) {
            mDriverMarker.remove();
        }
        mRequest.setText("Request An Ambulance");

        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhone.setText("");

    }



    private void getDirections(LatLng _driverLatLng)  {
        LatLng currentLatLang = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(BuildConfig.MAPS_API_KEY)
                .build();
        DirectionsResult pickupDirectionsresult = null;
        try {
            pickupDirectionsresult = DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .origin(new com.google.maps.model.LatLng(_driverLatLng.latitude, _driverLatLng.longitude))
                    .destination(new com.google.maps.model.LatLng(currentLatLang.latitude, currentLatLang.longitude)).await();
        } catch (Exception e) {
            Toast.makeText(CustomerMapActivity.this, " Error getting ambulance directions \n" + e.toString(),
                    Toast.LENGTH_LONG).show();
        }

        if (pickupDirectionsresult != null && pickupDirectionsresult.routes != null && pickupDirectionsresult.routes.length > 0) {
            drawRouteOnMap(pickupDirectionsresult.routes[0], Color.argb(255, 0, 0, 0));
        }

        DirectionsResult hospitalDirectionsresult = null;
        try {
            hospitalDirectionsresult = DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .origin(new com.google.maps.model.LatLng(currentLatLang.latitude, currentLatLang.longitude))
                    .destination(new com.google.maps.model.LatLng(destinationLatlng.latitude, destinationLatlng.longitude)).await();
        } catch (Exception e) {
            Toast.makeText(CustomerMapActivity.this, " Error hospital getting directions \n" + e.toString(),
                    Toast.LENGTH_LONG).show();
        }

        if (hospitalDirectionsresult != null && hospitalDirectionsresult.routes != null && hospitalDirectionsresult.routes.length > 0) {
            drawRouteOnMap(hospitalDirectionsresult.routes[0], Color.argb(255, 255, 0, 0));
        }

    }


    private void drawRouteOnMap(com.google.maps.model.DirectionsRoute route, int color) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mMap.setMyLocationEnabled(true);
        PolylineOptions polylineOptions = new PolylineOptions();

        for (com.google.maps.model.LatLng point : route.overviewPolyline.decodePath()) {
            LatLng latLng = new LatLng(point.lat, point.lng);
            polylineOptions.add(latLng);
        }
        polylineOptions.color(color);
        polylineOptions.width(10);
        polylineOptions.clickable(true);


        Polyline polyline = mMap.addPolyline(polylineOptions);

    }

    private void fetchHospitalsAround() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Hospitals");
        ref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull @NotNull DataSnapshot snapshot, @Nullable @org.jetbrains.annotations.Nullable String previousChildName) {
                Log.d(TAG, "hospital added");
                String hospitalName = snapshot.getKey();
                double lat = (double) snapshot.child("l/0").getValue();
                double longt = (double) snapshot.child("l/1").getValue();


                Bitmap bitmap = ((BitmapDrawable)getResources().getDrawable(R.mipmap.hospital)).getBitmap();
                Bitmap scaledBitmap =  Bitmap.createScaledBitmap(bitmap, 128, 128, false);
                Marker marker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, longt)).title(hospitalName).icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap)));
                marker.setTag("Hospital");
                hospitalMarkers.add(marker);
            }

            @Override
            public void onChildChanged(@NonNull @NotNull DataSnapshot snapshot, @Nullable @org.jetbrains.annotations.Nullable String previousChildName) {
                Log.d(TAG, "hospital added");
                String hospitalName = snapshot.getKey();
                double lat = (double) snapshot.child("l/0").getValue();
                double longt = (double) snapshot.child("l/1").getValue();

                for (Marker _m: hospitalMarkers
                     ) {
                    if(_m.getTitle() == hospitalName){
                        _m.remove();
                    }
                }

                Bitmap bitmap = ((BitmapDrawable)getResources().getDrawable(R.mipmap.hospital)).getBitmap();
                Bitmap scaledBitmap =  Bitmap.createScaledBitmap(bitmap, 128, 128, false);
                Marker marker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, longt)).title(hospitalName).icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap)));
                marker.setTag("Hospital");
                hospitalMarkers.add(marker);

            }

            @Override
            public void onChildRemoved(@NonNull @NotNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull @NotNull DataSnapshot snapshot, @Nullable @org.jetbrains.annotations.Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {

            }
        });
    }

   }