package com.digitalvotingpass.digitalvotingpass;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.digitalvotingpass.blockchain.BlockChain;
import com.digitalvotingpass.blockchain.BlockchainCallBackListener;
import com.digitalvotingpass.electionchoice.Election;
import com.digitalvotingpass.electionchoice.ElectionChoiceActivity;
import com.digitalvotingpass.utilities.ErrorDialog;
import com.digitalvotingpass.utilities.Util;
import com.google.gson.Gson;

import java.text.DecimalFormat;
import java.util.Date;

public class SplashActivity extends Activity implements BlockchainCallBackListener {
    public static final int REQUEST_CODE_STORAGE = 15;
    private int DELAY_INIT_TEXT_UPDATES = 800;

    private TextView downloadProgressText;
    private TextView currentTask;
    private ProgressBar downloadProgressBar;
    private Activity thisActivity;
    private Handler handler;
    private Handler initTextHandler;
    private BlockChain blockChain;
    private Typeface typeFace;

    DecimalFormat percentFormatter = new DecimalFormat("##0.0");

    Runnable startBlockChain = new Runnable(){
        @Override
        public void run() {
            if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermissions();
                return;
            }
            blockChain.addListener((BlockchainCallBackListener) thisActivity);
            blockChain.startDownload();
        }
    };

    Runnable initTextUpdater = new Runnable() {
        int i = 0;
        @Override
        public void run() {
            String[] s = thisActivity.getResources().getStringArray(R.array.init_array);
            currentTask.setText(s[i % s.length]);
            i++;
            initTextHandler.postDelayed(this, DELAY_INIT_TEXT_UPDATES);
        }
    };

    /**
     * Creates a splash screen
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        thisActivity = this;

        downloadProgressText = (TextView) findViewById(R.id.download_progress_text);
        currentTask = (TextView) findViewById(R.id.progress_current_task);
        downloadProgressBar = (ProgressBar) findViewById(R.id.download_progress_bar);

        typeFace = Util.getMainFont(getAssets());
        downloadProgressText.setTypeface(typeFace);
        currentTask.setTypeface(typeFace);
        ((TextView) findViewById(R.id.text_splash_screen)).setTypeface(typeFace);

        if (savedInstanceState == null) {
            try {
                blockChain = BlockChain.getInstance(getApplicationContext());
                handler = new Handler();
                initTextHandler = new Handler();
                initTextHandler.post(initTextUpdater);
                handler.post(startBlockChain);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * When the view is focused, check network state and display an error when network is unavailable.
     * @param hasFocus
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!Util.isOnline(getApplicationContext()) && hasFocus) {
            initTextHandler.removeCallbacks(initTextUpdater);
            currentTask.setText(getString(R.string.no_connection));

            if (!Util.isNetEnabled(getApplicationContext())) {
                showSnackbar();
            }
        }
    }

    public void showSnackbar() {
        View.OnClickListener inputSnackbarListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        };
        Snackbar snackbar = Snackbar.make(findViewById(R.id.splash_screen_layout), getString(R.string.please_enable_connect_message), Snackbar.LENGTH_INDEFINITE);
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.redFailed));
        TextView textView = (TextView) (snackbar.getView()).findViewById(android.support.design.R.id.snackbar_text);
        TextView actionTextView = (TextView) (snackbar.getView()).findViewById(android.support.design.R.id.snackbar_action);
        textView.setTypeface(typeFace);
        actionTextView.setTypeface(typeFace);
        snackbar.setAction(R.string.go_network_settings, inputSnackbarListener);
        snackbar.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            } else {
                handler.post(startBlockChain);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void requestStoragePermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ErrorDialog.newInstance(getString(R.string.storage_permission_explanation))
                    .show(getFragmentManager(), "");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE);
        }
    }

    @Override
    public void onInitComplete() {
        initTextHandler.removeCallbacks(initTextUpdater);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentTask.setText(R.string.downloading_text);
                downloadProgressText.setText(percentFormatter.format(0) + "%");
            }
        });
    }

    /** When download is complete, go to the next activity
     *  Create an Intent that will start either the Election choice or the mainactivity
     *  based on whether or not an election was already selected and still exists on the blockchain.
     */
    @Override
    public void onDownloadComplete() {
        // Create an Intent that will start either the Election choice or the mainactivity
        // based on whether or not an election was already selected.
        SharedPreferences sharedPrefs = getSharedPreferences(getString(R.string.shared_preferences_file), Context.MODE_PRIVATE);
        String json = sharedPrefs.getString(getString(R.string.shared_preferences_key_election), "not found");
        Intent intent;

        // Check if the election exists in sharedpreferences and in the blockchain
        if(json.equals("not found")) {
            intent = new Intent(SplashActivity.this, ElectionChoiceActivity.class);
        } else {
            Gson gson = new Gson();
            Election election = gson.fromJson(json, Election.class);
            if(!blockChain.assetExists(election.getAsset())){
                intent = new Intent(SplashActivity.this, ElectionChoiceActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }
        }
        thisActivity.startActivity(intent);
        thisActivity.finish();
    }

    @Override
    protected void onDestroy() {
        //remove this listener
        blockChain.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onDownloadProgress(final double pct, int blocksSoFar, Date date) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentTask.setText(R.string.downloading_text);
                downloadProgressText.setText(percentFormatter.format(pct) + "%");
                downloadProgressBar.setProgress((int)pct);
            }
        });
    }
}
