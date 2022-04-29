package com.adv.tms.stomp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.adv.tms.stomp.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        /* Connection test. */
        Log.v(TAG, "onCreate: connect...");
        showToast("onCreate: connect...", 100L);
        final WSMessaging.Agent wsAgent = new WSMessaging.Agent();
        wsAgent.set(new WSMessaging.Stomp.Callback() {
            @Override
            public void onReceive(String topic, WSMessaging.Stomp.Frame frame) {
                Log.v(TAG, "onReceive: " + topic + ", " + frame);
                showToast("onReceive: " + topic + ", " + frame, 2000L);
            }
        });
        wsAgent.subscribe("/topic/greetings");
        wsAgent.connect(new WSMessaging.Dest.Builder()
                .setClientID("TmsTms")
                .setUrl("ws://172.22.24.82:8080/cm-websocket")
                .setTimeout(5000L)
                .build());

        /* Hello test. */
        binding.getRoot().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "run: sendJson...");
                showToast("run: sendJson to /app/hello with name " + wsAgent.getDest().clientID, 100L);
                wsAgent.sendJson("/app/hello", "{\"name\":\"" + wsAgent.getDest().clientID + "\"}");
            }
        }, 10000L);

        /* Disconnect test. */
        binding.getRoot().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "run: disconnect...");
                showToast("run: disconnect", 100L);
                wsAgent.disconnect();
            }
        }, 10 * 60 * 1000L);
    }

    private void showToast(final String message, final long delayInMs) {
        binding.getRoot().postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }, delayInMs);
    }
}