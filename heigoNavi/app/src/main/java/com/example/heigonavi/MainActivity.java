package com.example.heigonavi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationEventListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.utils.LocaleUtils;
import com.mapbox.services.android.ui.geocoder.GeocoderAutoCompleteView;

import java.lang.ref.WeakReference;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

public class MainActivity extends AppCompatActivity implements
        LocationEngineCallback, PermissionsListener, OnMapReadyCallback,
        Callback<DirectionsResponse>, NavigationEventListener, OffRouteListener,
        ProgressChangeListener {

    private static final String SOURCE_ID = "SOURCE_ID";
    private static final String ICON_ID = "ICON_ID";
    private static final String LAYER_ID = "LAYER_ID";
    private static final String TAG = "DirectionsActivity";
    public MapView mapView;
    GeocoderAutoCompleteView autoComplete;
    private MapboxMap map;
    private PermissionsManager permissionsManager;
    private LocationComponent locationComponent;
    private LocationEngine locationEngine;
    private long DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L;
    private long DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5;
    private MainActivityLocationCallback callback = new MainActivityLocationCallback(this);
    private DirectionsRoute route;
    private LocaleUtils localeUtils;
    private Point originPosition;
    private Point destinationPosition;
    private Button button;
    private DirectionsRoute currentRoute;
    private NavigationMapRoute navigationMapRoute;
    private String geojsonSourceLayerId = "geojsonSourceLayerId";
    private String symbolIconId = "symbolIconId";
    private Polyline polyline;
    private boolean running;
    private boolean tracking;
    private boolean wasInTunnel = false;
    private Marker destinationMarker;
    private LatLng originCoord;
    private LatLng destinationCoord;
    private Location originLocation;
    private TextView points;
    private ImageButton dataViewButton;

    private String getUnitTypeFromSharedPreferences() {
        String unitType = localeUtils.getUnitTypeForDeviceLocale(this);
        return unitType;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // access token :
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        localeUtils = new LocaleUtils();

        dataViewButton = findViewById(R.id.menuDataView);

        // Set up Tabbed view button
        dataViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent userMenuIntent = new Intent(MainActivity.this, MenuActivity.class);
                MainActivity.this.startActivity(userMenuIntent);

            }
        });

        Point destination = destinationPosition;
        Point origin = originPosition;

        button = findViewById(R.id.button);
        // Add listener to start button. Start navigation
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean simulateRoute = false;
                NavigationLauncherOptions launchOptions = NavigationLauncherOptions.builder()
                        .directionsRoute(currentRoute)
                        .shouldSimulateRoute(simulateRoute)
                        .lightThemeResId(R.style.NavigationMapRoute)
                        .build();

                NavigationLauncher.startNavigation(MainActivity.this, launchOptions);

                NavigationViewOptions.Builder viewOptions = NavigationViewOptions.builder();

                viewOptions.directionsRoute(currentRoute);
                viewOptions.shouldSimulateRoute(false);

                MapboxNavigationOptions.Builder navOptions = MapboxNavigationOptions.builder();

                navOptions.enableFasterRouteDetection(true);
                navOptions.defaultMilestonesEnabled(true);
                navOptions.defaultMilestonesEnabled(true);
                viewOptions.navigationOptions(navOptions.build());

            }
        });

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                map = mapboxMap;

                mapboxMap.setStyle(new Style.Builder().fromUri("mapbox://styles/heigoankuhin/ck6rlknjj0iz11iocc6dco3cq"), new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        initSearchFab();
                        enableLocationComponent(style);

                        setUpSource(style);
                        setupLayer(style);

                    }
                });


                if (originLocation == null) {
                    // Random default value for when origin initialization fails
                    originCoord = new LatLng(53.11, 52.11);
                } else {
                    originCoord = new LatLng(originLocation.getLatitude(), originLocation.getLongitude());
                }

                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {

                    @Override
                    public boolean onMapClick(@NonNull LatLng point) {
                        //hideOnScreenKeyboard();
                        //Log.d("DEBUG", "Map click registered");
                        //Log.d("Coordinates", originCoord.toString() );

                        if (destinationMarker != null) {
                            mapboxMap.removeMarker(destinationMarker);
                        }

                        destinationCoord = point;

                        destinationMarker = mapboxMap.addMarker(new MarkerOptions().position(destinationCoord));

                        destinationPosition = Point.fromLngLat(destinationCoord.getLongitude(), destinationCoord.getLatitude());
                        originPosition = Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude());
                        getRoute(originPosition, destinationPosition);

                        button.setEnabled(true);

                        button.setBackgroundResource(R.color.mapbox_blue);

                        return true;
                    }

                });
            }
        });

        setUpMenu();
        new pointInitUpdater(this).execute();
    }

    private void initSearchFab() {
        findViewById(R.id.autoComplete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new PlaceAutocomplete.IntentBuilder()
                        .accessToken(Mapbox.getAccessToken() != null ? Mapbox.getAccessToken() : getString(R.string.mapbox_access_token))
                        .placeOptions(PlaceOptions.builder()
                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                .limit(10)
                                //.addInjectedFeature(home)
                                //.addInjectedFeature(work)
                                .build(PlaceOptions.MODE_CARDS))
                        .build(MainActivity.this);
                startActivityForResult(intent, 1);

            }
        });
    }


    private void setUpSource(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addSource(new GeoJsonSource(geojsonSourceLayerId));
    }

    private void setupLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addLayer(new SymbolLayer("SYMBOL_LAYER_ID", geojsonSourceLayerId).withProperties(
                iconImage(symbolIconId),
                iconOffset(new Float[]{0f, -8f})
        ));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == 1) {

// Retrieve selected location's CarmenFeature
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);

// Create a new FeatureCollection and add a new Feature to it using selectedCarmenFeature above.
// Then retrieve and update the source designated for showing a selected location's symbol layer icon

            if (map != null) {
                Style style = map.getStyle();
                if (style != null) {
                    GeoJsonSource source = style.getSourceAs(geojsonSourceLayerId);
                    if (source != null) {
                        source.setGeoJson(FeatureCollection.fromFeatures(
                                new Feature[]{Feature.fromJson(selectedCarmenFeature.toJson())}));
                    }

                    if (destinationMarker != null) {
                        map.removeMarker(destinationMarker);
                    }
                    destinationPosition = ((Point) selectedCarmenFeature.geometry());
                    destinationCoord = new LatLng(destinationPosition.latitude(), destinationPosition.longitude());
                    // Move map camera to the selected location
                    destinationMarker = map.addMarker(new MarkerOptions().position(destinationCoord));

                    destinationPosition = Point.fromLngLat(destinationCoord.getLongitude(), destinationCoord.getLatitude());
                    originPosition = Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude());
                    getRoute(originPosition, destinationPosition);

                    button.setEnabled(true);

                    button.setBackgroundResource(R.color.mapbox_blue);
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                                    .target(new LatLng(((Point) selectedCarmenFeature.geometry()).latitude(),
                                            ((Point) selectedCarmenFeature.geometry()).longitude()))
                                    .zoom(14)
                                    .build()), 4000);
                }
            }
        }
    }


    private void setUpMenu() {
        // TODO: POINT SYSTEM?
    }

    private void getRoute(Point origin, Point destination) {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(originPosition)
                .destination(destinationPosition)
                .voiceUnits("metric")
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        // You can get the generic HTTP info about the response
                        Log.d(TAG, "Response code: " + response.code());
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Log.e(TAG, "No routes found");
                            return;
                        }

                        currentRoute = response.body().routes().get(0);

                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, map, R.style.NavigationMapRoute);
                        }
                        // track progress to remove line??
                        //navigationMapRoute.addProgressChangeListener(null);
                        navigationMapRoute.addRoute(currentRoute);
                        navigationMapRoute.showAlternativeRoutes(true);

                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                        Log.e(TAG, "Error: " + throwable.getMessage());
                    }
                });
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {

        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // custom location icon options
            LocationComponentOptions customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .elevation(5)
                    .accuracyAlpha(.6f)
                    .accuracyColor(R.color.accuracyColor)
                    .foregroundDrawable(R.mipmap.car_icon2_foreground)
                    .build();
            // Create an instance of LOST location engine
            Log.d("Location:", "Location Engine initialized.");
            LocationComponent locationComponent = map.getLocationComponent();
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .locationComponentOptions(customLocationComponentOptions)
                            .useDefaultLocationEngine(false)
                            .build();
            locationComponent.activateLocationComponent(locationComponentActivationOptions);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.COMPASS);

            initializeLocationEngine();

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings({"MissingPermission"})
    private void initializeLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this);

        LocationEngineRequest request = new LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
                .build();

        locationEngine.requestLocationUpdates(request, callback, getMainLooper());
        locationEngine.getLastLocation(callback);


        if (originLocation != null) {
            //originLocation = locationEngine.getLastLocation();
            setCameraPosition(originLocation);
        } else {
            //(this);
        }
    }

    private void setCameraPosition(Location location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(location.getLatitude(), location.getLongitude()), 13));
    }

    @Override
    public void onRunning(boolean running) {
        this.running = running;
        if (running) {
            //navigation.addOffRouteListener(this);
            //navigation.addProgressChangeListener(this);
        }

    }


    @Override
    public void userOffRoute(Location location) {
        originPosition = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        getRoute(originPosition, destinationPosition);
        Snackbar.make(mapView, "User Off Route", Snackbar.LENGTH_SHORT).show();
        map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())));

    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {

        boolean isInTunnel = routeProgress.inTunnel();
        location = location;
        if (!wasInTunnel && isInTunnel) {
            wasInTunnel = true;
            Snackbar.make(mapView, "Entered tunnel!", Snackbar.LENGTH_SHORT).show();
        }
        if (wasInTunnel && !isInTunnel) {
            wasInTunnel = false;
            Snackbar.make(mapView, "Exited tunnel!", Snackbar.LENGTH_SHORT).show();
        }
        if (tracking) {
            map.getLocationComponent().forceLocationUpdate(location);
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .zoom(15)
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .bearing(location.getBearing())
                    .build();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000);
        }
        //instructionView.updateDistanceWith(routeProgress);
    }


    @Override
    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {

    }

    @Override
    public void onFailure(Call<DirectionsResponse> call, Throwable t) {

    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

    }

    @Override
    public void onPermissionResult(boolean granted) {

    }

    @Override
    public void onSuccess(Object result) {

    }

    @Override
    public void onFailure(@NonNull Exception exception) {

    }

    private void hideOnScreenKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private static class MainActivityLocationCallback
            implements LocationEngineCallback<LocationEngineResult> {

        private final WeakReference<MainActivity> activityWeakReference;

        MainActivityLocationCallback(MainActivity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(LocationEngineResult result) {
            MainActivity activity = activityWeakReference.get();

            if (activity != null) {
                Location location = result.getLastLocation();
                activity.originLocation = location;

                if (location == null) {
                    return;
                }

                /* FOR DEBUG:
                 Toast.makeText(activity, String.format(activity.getString(R.string.new_location),
                        String.valueOf(result.getLastLocation().getLatitude()), String.valueOf(result.getLastLocation()
                        .getLongitude())),
                        Toast.LENGTH_SHORT).show();
                 */

                if (activity.map != null && result.getLastLocation() != null) {
                    activity.map.getLocationComponent().forceLocationUpdate(result.getLastLocation());
                }
            }
        }

        @Override
        public void onFailure(@NonNull Exception exception) {
            Log.d("LocationChangeActivity", exception.getLocalizedMessage());
            MainActivity activity = activityWeakReference.get();
            if (activity != null) {
                Toast.makeText(activity, exception.getLocalizedMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Point async updater for use only in this context
    private static class pointInitUpdater extends AsyncTask {

        private WeakReference<Context> contextReference;

        pointInitUpdater(Context context) {
            contextReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Object[] objects) {

            try {
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Log.e("TAG", e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            AppCompatActivity context = (AppCompatActivity) contextReference.get();

        }
    }
}


