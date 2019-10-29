package com.molly.bustracker;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.molly.bustracker.model.BusTime;
import com.molly.bustracker.model.ClusterMarker;
import com.molly.bustracker.model.Locations;
import com.molly.bustracker.model.PolylineData;
import com.molly.bustracker.model.UserLocation;
import com.molly.bustracker.util.MyClusterManagerRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainMapFragment extends Fragment implements
        OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnPolylineClickListener, View.OnClickListener {

    private static final String TAG = "MainMapFragment";
    private static final int LOCATION_UPDATE_INTERVAL = 3000;

    private MapView mMapView;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private ArrayList<UserLocation> mUserLocations = new ArrayList<>();

    private ArrayList<BusTime> listBusTime = new ArrayList<>();
    private ArrayList<Locations> mLocations = new ArrayList<>();
    private GoogleMap mGoogleMap;
    private LatLngBounds mMapBoundary;
    // private UserLocation mUserPosition;
    private Locations mUserPosition;
    final private ArrayList<Locations> mDriverLocations = new ArrayList<Locations>();
    private ClusterManager mClusterManager;
    private MyClusterManagerRenderer mClusterManagerRenderer;
    private ArrayList<ClusterMarker> mClusterMarkers = new ArrayList<>();
    private Handler mHandler = new Handler();
    private Runnable mRunnable;
    private GeoApiContext mGeoApiContext = null;
    private ArrayList<PolylineData> mPolyLinesData = new ArrayList<>();
    private Marker mSelectedMarker = null;
    private ArrayList<Marker> mTripMarkers = new ArrayList<>();
    private FirebaseFirestore mDb;
    private ListenerRegistration mTripEventListener;

    private LinearLayout mETAParentLayout;
    private TextView mBusID1, mBusID2, mBusID3, mETA1, mETA2, mETA3;
    private Button mButtonDismiss;
    private ProgressBar progressBar;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {

//            final ArrayList<UserLocation> locations = getArguments().getParcelableArrayList(getString(R.string.intent_user_locations));
            final ArrayList<Locations> locations = getArguments().getParcelableArrayList(getString(R.string.intent_user_locations));
//            mUserLocations.clear();
//            mUserLocations.addAll(locations);
            mLocations.clear();
            mLocations.addAll(locations);
        }

        mDb = FirebaseFirestore.getInstance();
        setUserPosition();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_map, container, false);
        mMapView = view.findViewById(R.id.navigation_map);
        view.findViewById(R.id.btn_reset_map).setOnClickListener(this);

        mButtonDismiss = view.findViewById(R.id.btn_dismiss);
        mButtonDismiss.setOnClickListener(this);
        mBusID1 = view.findViewById(R.id.bus_id1);
        mBusID2 = view.findViewById(R.id.bus_id2);
        mBusID3 = view.findViewById(R.id.bus_id3);
        mETA1 = view.findViewById(R.id.eta1);
        mETA2 = view.findViewById(R.id.eta2);
        mETA3 = view.findViewById(R.id.eta3);
        mETAParentLayout = view.findViewById(R.id.eta_layout);
        mETAParentLayout.setVisibility(View.GONE);
        progressBar = view.findViewById(R.id.progressBar2);
        progressBar.setVisibility(View.GONE);

        initGoogleMap(savedInstanceState);


        return view;
    }

    private void resetMap() {
        if (mGoogleMap != null) {
            mGoogleMap.clear();

            if (mClusterManager != null) {
                mClusterManager.clearItems();
            }

            if (mClusterMarkers.size() > 0) {
                mClusterMarkers.clear();
                mClusterMarkers = new ArrayList<>();
            }

            if (mPolyLinesData.size() > 0) {
                mPolyLinesData.clear();
                mPolyLinesData = new ArrayList<>();
            }
        }
    }

    public void zoomRoute(List<LatLng> lstLatLngRoute) {

        if (mGoogleMap == null || lstLatLngRoute == null || lstLatLngRoute.isEmpty()) return;

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng latLngPoint : lstLatLngRoute)
            boundsBuilder.include(latLngPoint);

        int routePadding = 120;
        LatLngBounds latLngBounds = boundsBuilder.build();

        mGoogleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, routePadding),
                600,
                null
        );
    }

    private void removeTripMarkers() {
        for (Marker marker : mTripMarkers) {
            marker.remove();
        }
    }

    private void resetSelectedMarker() {
        if (mSelectedMarker != null) {
            mSelectedMarker.setVisible(true);
            mSelectedMarker = null;
            removeTripMarkers();
        }
    }


    private void calculateDirections(final Marker marker, final Locations locations, final int count) {
//        Log.d(TAG, "calculateDirections: calculating directions.");

        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                marker.getPosition().latitude,
                marker.getPosition().longitude
        );
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);


        Log.d(TAG, "location ada: " + locations.getName());

        directions.origin(
                new com.google.maps.model.LatLng(
                        locations.getGeo_point().getLatitude(),
                        locations.getGeo_point().getLongitude()
                )
        );
        Log.d(TAG, "calculateDirections: destination: " + destination.toString());
        directions.destination(destination);
        directions.setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
//                Log.d(TAG, "calculateDirections: routes: " + result.routes[0].toString());
//                Log.d(TAG, "calculateDirections: duration: " + result.routes[0].legs[0].duration);
//                Log.d(TAG, "calculateDirections: distance: " + result.routes[0].legs[0].distance);
//                Log.d(TAG, "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());


                long finalDuration = 0;
                for (DirectionsRoute directionsRoute : result.routes) {
                    long tempDuration = directionsRoute.legs[0].duration.inSeconds;
                    finalDuration = tempDuration;
                }

                Log.d(TAG, "final duration: " + finalDuration);
                insertETA(count, finalDuration, locations);

            }

            @Override
            public void onFailure(Throwable e) {
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.getMessage());

            }
        });


    }

    private void insertETA(int count, long duration, Locations locations) {

        long minutes = (duration % 3600) / 60;
        String newMinutes = Long.toString(minutes);

        BusTime bustime = new BusTime(newMinutes, locations.getName());

        if (count == 0) {
            mBusID1.setText(bustime.getName());
            mETA1.setText(bustime.getDuration());
        }
        if (count == 1) {

            mBusID2.setText(bustime.getName());
            mETA2.setText(bustime.getDuration());
        }
        if (count == 2) {
            mBusID3.setText(bustime.getName());
            mETA3.setText(bustime.getDuration());
        }

        if (count > 2) {
            rearrangeTable(bustime);
        }


    }

    private void rearrangeTable(BusTime newbusTime) {

        BusTime btTable1 = new BusTime(mETA1.getText().toString(), mBusID1.getText().toString());
        BusTime btTable2 = new BusTime(mETA2.getText().toString(), mBusID2.getText().toString());
        BusTime btTable3 = new BusTime(mETA3.getText().toString(), mBusID3.getText().toString());

        ArrayList<BusTime> tempbtList = new ArrayList<>();
        tempbtList.clear();
        tempbtList.add(btTable1);
        tempbtList.add(btTable2);
        tempbtList.add(btTable3);
        tempbtList.add(newbusTime);

        Collections.sort(tempbtList, (s1, s2) -> s1.getDuration().compareToIgnoreCase(s2.getDuration()));

        mBusID1.setText(tempbtList.get(0).getName());
        mETA1.setText(tempbtList.get(0).getDuration());
        mBusID2.setText(tempbtList.get(1).getName());
        mETA2.setText(tempbtList.get(1).getDuration());
        mBusID3.setText(tempbtList.get(2).getName());
        mETA3.setText(tempbtList.get(2).getDuration());

//        for (BusTime bt : tempbtList){
//            Log.d(TAG, "time: " + bt.getName());
//        }

    }


    private void startUserLocationsRunnable() {
        Log.d(TAG, "startUserLocationsRunnable: starting runnable for retrieving updated locations.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                retrieveDriverLocations();
                //retrievePlacesLocations();
                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates() {
        mHandler.removeCallbacks(mRunnable);
    }

    private void retrieveDriverLocations() {

        try {
            for (final ClusterMarker clusterMarker : mClusterMarkers) {

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection(getString(R.string.collection_driver_locations))
                        .document(clusterMarker.getId());

                userLocationRef.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        final Locations updatedLocations = task.getResult().toObject(Locations.class);

                        for (int i = 0; i < mClusterMarkers.size(); i++) {
                            try {
                                if (mClusterMarkers.get(i).getId().equals(updatedLocations.getId())) {

                                    LatLng updatedLatLng = new LatLng(
                                            updatedLocations.getGeo_point().getLatitude(),
                                            updatedLocations.getGeo_point().getLongitude()
                                    );

                                    mClusterMarkers.get(i).setPosition(updatedLatLng);
                                    mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));
                                }


                            } catch (NullPointerException e) {
                                Log.e(TAG, "retrieveDriverLocations: NullPointerException: " + e.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query. Ending query." + e.getMessage());
        }

    }

    private void retrievePlacesLocations() {
        //   Log.d(TAG, "retrieveUserLocations: retrieving location of all users in the chatroom.");

        try {
            for (final ClusterMarker clusterMarker : mClusterMarkers) {

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection(getString(R.string.collection_place_locations))
                        .document(clusterMarker.getId());

                userLocationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {

//                            final UserLocation updatedUserLocation = task.getResult().toObject(UserLocation.class);
                            final Locations updatedLocations = task.getResult().toObject(Locations.class);

                            // update the location
                            for (int i = 0; i < mClusterMarkers.size(); i++) {
                                try {
                                    if (mClusterMarkers.get(i).getId().equals(updatedLocations.getId())) {

                                        LatLng updatedLatLng = new LatLng(
                                                updatedLocations.getGeo_point().getLatitude(),
                                                updatedLocations.getGeo_point().getLongitude()
                                        );

                                        mClusterMarkers.get(i).setPosition(updatedLatLng);
                                        mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));
                                    }


                                } catch (NullPointerException e) {
                                    Log.e(TAG, "retrieveUserLocations: NullPointerException: " + e.getMessage());
                                }
                            }
                        }
                    }
                });
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query. Ending query." + e.getMessage());
        }

    }

    private void addMapMarkers() {

        if (mGoogleMap != null) {

            resetMap();

            if (mClusterManager == null) {
                mClusterManager = new ClusterManager<ClusterMarker>(getActivity().getApplicationContext(), mGoogleMap);
            }
            if (mClusterManagerRenderer == null) {
                mClusterManagerRenderer = new MyClusterManagerRenderer(
                        getActivity(),
                        mGoogleMap,
                        mClusterManager
                );
                mClusterManager.setRenderer(mClusterManagerRenderer);
            }

            if (mLocations.size() != 0) {
                mDriverLocations.clear();
                for (Locations locations : mLocations) {
                    try {
                        String snippet = "";
                        int avatar = 0;

                        if (locations.getId().equals(FirebaseAuth.getInstance().getUid())) {
                            snippet = "This is you";
                            avatar = R.drawable.ic_person_black_24dp;
                        } else if (locations.getStatus().equals("Driver")) {
                            snippet = "Driver";
                            avatar = R.drawable.ic_directions_car_black_24dp;
                        } else if (locations.getStatus().equals("Places")) {
                            snippet = "Check bus ETA for " + locations.getName() + "?";
                            avatar = R.drawable.ic_place_black_24dp;
                        } else if (locations.getStatus().equals("Passenger")) {
                            snippet = "Passenger";
                        } else {
                            snippet = "ignore";
                        }

                        ClusterMarker newClusterMarker = new ClusterMarker(
                                new LatLng(locations.getGeo_point().getLatitude(), locations.getGeo_point().getLongitude()),
                                locations.getName(),
                                snippet,
                                locations.getId(),
                                avatar
                        );

                        String status = locations.getStatus();

                        if (status != "Passenger") {
                            mClusterManager.addItem(newClusterMarker);
                            mClusterMarkers.add(newClusterMarker);
                        }

                        if (status.equals("Driver")) {
                            mDriverLocations.add(locations);
                        }

                    } catch (NullPointerException e) {
                        Log.e(TAG, "addMapMarkers: NullPointerException: " + e.getMessage());
                    }
                }
                mClusterManager.cluster();
                setCameraView();
            }
        }
    }


    private void setCameraView() {


        double bottomBoundary = mUserPosition.getGeo_point().getLatitude() - .01;
        double leftBoundary = mUserPosition.getGeo_point().getLongitude() - .01;
        double topBoundary = mUserPosition.getGeo_point().getLatitude() + .01;
        double rightBoundary = mUserPosition.getGeo_point().getLongitude() + .01;

//        double bottomBoundary = 4.3877663 - .1;
//        double leftBoundary = 100.9639676 - .1;
//        double topBoundary = 4.3877663 + .1;
//        double rightBoundary = 100.9639676 + .1;

        mMapBoundary = new LatLngBounds(
                new LatLng(bottomBoundary, leftBoundary),
                new LatLng(topBoundary, rightBoundary)
        );

        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMapBoundary, 0));
    }

    private void setUserPosition() {
        for (Locations locations : mLocations) {
            if (locations.getId().equals(FirebaseAuth.getInstance().getUid())) {
                mUserPosition = locations;
            }
        }

    }


    public static MainMapFragment newInstance() {
        return new MainMapFragment();
    }


    private void initGoogleMap(Bundle savedInstanceState) {
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        mMapView.onCreate(mapViewBundle);

        mMapView.getMapAsync(this);

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.google_maps_api_key))
                    .build();
        }

    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
        startUserLocationsRunnable();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        // map.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //map.setMyLocationEnabled(true);
        mGoogleMap = map;
        mGoogleMap.setOnPolylineClickListener(this);
        addMapMarkers();
        mGoogleMap.setOnInfoWindowClickListener(this);
    }

    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
        stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }


    @Override
    public void onInfoWindowClick(final Marker marker) {
        if (marker.getTitle().contains("Trip #")) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage("Open Google Maps?")
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {

                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        } else {
            if ((marker.getSnippet().equals("This is you")) || (marker.getSnippet().equals("Driver"))
                    || (marker.getSnippet().equals("Passenger")) || (marker.getSnippet().equals("ignore"))) {
                marker.hideInfoWindow();
            } else {

                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(marker.getSnippet())
                        .setCancelable(true)
                        .setPositiveButton("Yes", (dialog, id) -> {
                            resetSelectedMarker();
                            mSelectedMarker = marker;

                            int count = 0;
                            for (Locations locations : mDriverLocations) {
                                calculateDirections(marker, locations, count);
                                count++;
                            }

                            progressBar.setVisibility(View.VISIBLE);
                            Handler handler = new Handler();
                            Runnable runnable = new Runnable() {
                                @Override
                                public void run() {
                                    mETAParentLayout.setVisibility(View.VISIBLE);
                                    progressBar.setVisibility(View.GONE);
                                }

                            };

                            handler.postDelayed(runnable, 3500);

                            dialog.dismiss();
                        })
                        .setNegativeButton("No", (dialog, id) -> dialog.cancel());
                final AlertDialog alert = builder.create();
                alert.show();
            }
        }

    }

    @Override
    public void onPolylineClick(Polyline polyline) {

        int index = 0;
        for (PolylineData polylineData : mPolyLinesData) {
            index++;
            Log.d(TAG, "onPolylineClick: toString: " + polylineData.toString());
            if (polyline.getId().equals(polylineData.getPolyline().getId())) {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.blue1));
                polylineData.getPolyline().setZIndex(1);

                LatLng endLocation = new LatLng(
                        polylineData.getLeg().endLocation.lat,
                        polylineData.getLeg().endLocation.lng
                );

                Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                        .position(endLocation)
                        .title("Trip #" + index)
                        .snippet("Duration: " + polylineData.getLeg().duration)

                );

                marker.showInfoWindow();
                mTripMarkers.add(marker);

            } else {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                polylineData.getPolyline().setZIndex(0);
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_reset_map: {
                addMapMarkers();
                startUserLocationsRunnable();
                break;
            }
            case R.id.btn_dismiss: {
                mETAParentLayout.setVisibility(View.GONE);
                addMapMarkers();
                startUserLocationsRunnable();
                break;
            }
        }
    }

}

