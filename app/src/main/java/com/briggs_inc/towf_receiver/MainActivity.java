package com.briggs_inc.towf_receiver;


import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import com.briggs_inc.towf_receiver.InfoService.InfoServiceBinder;
import com.briggs_inc.towf_receiver.R;
import com.briggs_inc.towf_receiver.NetworkPlaybackService.NpServiceBinder;
import android.support.v7.app.ActionBarActivity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import static com.briggs_inc.towf_receiver.MiscConstants.*;


public class MainActivity extends ActionBarActivity implements NetworkPlaybackServiceListener, InfoServiceListener {

    private static final String TAG = "MainActivity";

    // Setup GUI SharedPreferences Key's
    private static final String DESIRED_DELAY_KEY = "DesiredDelay";
    
    // GUI-related
	TextView wifiConnection;
	TextView waitingForServerLabel;
	LinearLayout streamViewLayout;
	LinearLayout listeningViewLayout;
    Button btnStartStop;
    Spinner languageSpinner;
    SeekBar desiredDelaySeekBar;
    TextView desiredDelayLabel;
    TextView lblReceivingAudio;
    TextView lblPlaybackSpeed;
    TextView debugResults;
    
    
    DatagramSocket infoSocket;
    
    ArrayList<LangPortPair> lppArrayList;
    ArrayAdapter<LangPortPair> languageSpinnerArrayAdapter;
    
    List<LangPortPair> lppList = new ArrayList<LangPortPair>();
    
    Intent npServiceIntent;
    Intent infoServiceIntent;
    
    NetworkPlaybackService npService;
    Boolean isBoundToNpService = false;
    
    InfoService infoService;
    Boolean isBoundToInfoService = false;
    
    int streamPort;
    
    private ServiceConnection npServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			isBoundToNpService = false;
			//npService.removeListener(MainActivity.this);  // Note though that onServiceDisconnected is not called on unbind()
			// Note: probably can't do above 'cuz the Service is gone
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.v(TAG, "onServiceConnected() Network Playback");
			NpServiceBinder npServiceBinder = (NpServiceBinder)service;
			npService = npServiceBinder.getService();
			isBoundToNpService = true;
			
			npService.addListener(MainActivity.this);
			
			updateGuiToReflectSystemState();
		}
	};
	
	private ServiceConnection infoServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			isBoundToInfoService = false;
			//infoService.removeListener(MainActivity.this);  // Note though that onServiceDisconnected is not called on unbind()
			// Note: probably can't do above 'cuz the Service is gone
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.v(TAG, "onServiceConnected() INFO");
			InfoServiceBinder infoServiceBinder = (InfoServiceBinder)service;
			infoService = infoServiceBinder.getService();
			isBoundToInfoService = true;
			
			infoService.addListener(MainActivity.this);
			
			updateGuiToReflectSystemState();
		}
	};

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.v(TAG, "------------------------");
        Log.v(TAG, "onCreate()");
        
        // GUI-related
        wifiConnection = (TextView) findViewById(R.id.wifiConnection);
        waitingForServerLabel = (TextView) findViewById(R.id.waitingForServerLabel);
        streamViewLayout = (LinearLayout) findViewById(R.id.streamViewLayout);
        listeningViewLayout = (LinearLayout) findViewById(R.id.listeningViewLayout);
        btnStartStop = (Button) findViewById(R.id.btnStartStop);
        languageSpinner = (Spinner) findViewById(R.id.languageSpinner);
        desiredDelaySeekBar = (SeekBar) findViewById(R.id.desiredDelaySeekBar);
        desiredDelayLabel = (TextView) findViewById(R.id.desiredDelayLabel);
        lblReceivingAudio = (TextView) findViewById(R.id.lblReceivingAudio);
        lblPlaybackSpeed = (TextView) findViewById(R.id.lblPlaybackSpeed);
        debugResults = (TextView) findViewById(R.id.debugResults);
        
        loadGuiPrefsFromFile();
        
        // Language Spinner Array Adapter
        languageSpinnerArrayAdapter = new ArrayAdapter<LangPortPair>(this, android.R.layout.simple_spinner_item, lppList);
        languageSpinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageSpinnerArrayAdapter);
        
        npServiceIntent = new Intent(this, NetworkPlaybackService.class);
        infoServiceIntent = new Intent(this, InfoService.class);
        
        desiredDelaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				float seconds = (float) (progress/100.0);
				desiredDelayLabel.setText(String.format("%1.2f",seconds));
				
				// Tell NpService that we want to change the Desired Delay
				if (isBoundToNpService && npService != null) {
					npService.onDesiredDelayChanged(seconds);
				}
			}
		});
        
        // INFO Service (Start)
        // Start it only if it's not already existing
        if (!isServiceRunning(InfoService.class)) {
        	Log.v(TAG, "Starting INFO Service");
        	startService(infoServiceIntent);
        }
    }
	
	private void updateLblPlaybackSpeed(int playbackSpeed) {
		if (playbackSpeed == PlaybackManager.PLAYBACK_SPEED_FASTER) {
			lblPlaybackSpeed.setText(String.format("%1.1fx", PlaybackManager.FASTER_PLAYBACK_MULTIPLIER));
			lblPlaybackSpeed.setTextColor(Color.BLUE);
		} else if (playbackSpeed == PlaybackManager.PLAYBACK_SPEED_SLOWER) {
			lblPlaybackSpeed.setText(String.format("%1.1fx", PlaybackManager.SLOWER_PLAYBACK_MULTIPLIER));
			lblPlaybackSpeed.setTextColor(Color.YELLOW);
		} else {
			lblPlaybackSpeed.setText("1x");
			lblPlaybackSpeed.setTextColor(Color.GREEN);
		}		
	}

	private void updateLblReceivingAudio(Boolean isReceivingAudio) {
		if (isReceivingAudio) {
			lblReceivingAudio.setTextColor(Color.GREEN);
		} else {
			lblReceivingAudio.setTextColor(Color.LTGRAY);
		}
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onStart() {
    	Log.v(TAG, "onStart()");
    	super.onStart();
    	
    	// NetworkPlaybackService (Bind)
    	if (isServiceRunning(NetworkPlaybackService.class)) {
    		bindService(npServiceIntent, npServiceConnection, Context.BIND_AUTO_CREATE);
    	}
    	
    	// INFO Service (Bind) - Note: INFO Service is started in onCreate()
    	if (isServiceRunning(InfoService.class)) {
    		bindService(infoServiceIntent, infoServiceConnection, Context.BIND_AUTO_CREATE);
    	}
    }
    
    @Override
    protected void onResume() {
    	Log.v(TAG, "onResume()");
    	
    	// Wi-Fi Label
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        wifiConnection.setText(wifiInfo.getSSID());
        
        updateGuiToReflectSystemState();
        
    	super.onResume();
    }
    
    private void updateGuiToReflectSystemState() {
    	// Let's make sure we know if server is currently streaming or not, so we can update GUI appropriately

    	// streamView visibility
        if (isBoundToInfoService && infoService != null) {
        	if (infoService.getIsServerStreaming()) {
	        	// Show streamView
        		waitingForServerLabel.setVisibility(View.INVISIBLE);
				streamViewLayout.setVisibility(View.VISIBLE);
				
				// Re-obtain lppList
				lppList.clear();
				lppList.addAll(infoService.lppList);
				languageSpinnerArrayAdapter.notifyDataSetChanged();
        	} else {
        		// Hide streamView
        		waitingForServerLabel.setVisibility(View.VISIBLE);
				streamViewLayout.setVisibility(View.INVISIBLE);
        	}
        } else {  // infoService not bound
        	// Hide streamView
        	waitingForServerLabel.setVisibility(View.VISIBLE);
			streamViewLayout.setVisibility(View.INVISIBLE);
        }
        
        // Start/Stop Button & listeningView visibility
        if (isBoundToNpService) {
        	btnStartStop.setText(R.string.stop_listening);
        	listeningViewLayout.setVisibility(View.VISIBLE);
        } else {
        	btnStartStop.setText(R.string.start_listening);
        	listeningViewLayout.setVisibility(View.INVISIBLE);
        }
        
        // receivingAudio & playbackSpeed labels
        if (isBoundToNpService && npService != null) {
        	// receivingAudio
        	updateLblReceivingAudio(npService.getIsReceivingAudio());
        	
        	// playbackSpeed
        	updateLblPlaybackSpeed(npService.getPlaybackSpeed());
        }
	}

	@Override
    protected void onPause() {
    	Log.v(TAG, "onPause()");
    	super.onPause();
    }
    
    @Override
    protected void onStop() {
    	Log.v(TAG, "onStop()");
    	super.onStop();
    	
    	// NetPlay Service (Unbind)
    	if (isBoundToNpService) {
    		npService.removeListener(this);
    		unbindService(npServiceConnection);
    		npService = null;  // ???
    		isBoundToNpService = false;
    	}
    	
    	//INFO Service (Unbind)
    	if (isBoundToInfoService) {
    		infoService.removeListener(this);
    		unbindService(infoServiceConnection);
    		infoService = null;  // ???
    		isBoundToInfoService = false;
    	}
    }
    
    @Override
    protected void onDestroy() {
    	Log.v(TAG, "onDestroy()");
    	super.onDestroy();
    }
    
    public void startStopListening(View v) {
    	if (isBoundToNpService) {
    		onStopListening();
    	} else {
    		onStartListening();
    	}
    }
    
    private void onStopListening() {
    	Log.v(TAG, "-----------------");
    	Log.v(TAG, "onStopListening()");
    	
    	// Unbind Np Service
    	if (isBoundToNpService) {
    		npService.removeListener(this);
    		unbindService(npServiceConnection);
    		npService = null;  // ???
    		isBoundToNpService = false;
    	}
    	
    	// Stop NpService
    	//npService.setIsListening(false);  //???Should I keep this??? If so, I'd better do it before npService = null!!!
    	stopService(npServiceIntent);  // Kill the service anyhow, in case it's hanging waiting for a packet that'll be long in coming
    	
    	// Labels
    	lblReceivingAudio.setTextColor(Color.LTGRAY);
    	lblPlaybackSpeed.setText("1x");
    	lblPlaybackSpeed.setTextColor(Color.LTGRAY);
    	
    	if (isBoundToInfoService) {
    		infoService.sendClientListening(false, streamPort);  // false => client NOT listening
    	}
    	
    	btnStartStop.setText(R.string.start_listening);
    	listeningViewLayout.setVisibility(View.INVISIBLE);
    }

    private void onStartListening() {
    	Log.v(TAG, "-----------------");
    	Log.v(TAG, "onStartListening()");
    	
    	saveGuiPrefsToFile();
    	
    	// Check that a valid language was selected
    	LangPortPair spinnerLpp = (LangPortPair)languageSpinner.getSelectedItem();
    	if (spinnerLpp == null) {
    		Log.e(TAG, "ERROR: No language was selected from the Spinner! Not Starting to Listen.");
    		return;
    	}
    	
    	// Start the NetworkPlaybackService
	    streamPort = ((LangPortPair)languageSpinner.getSelectedItem()).Port;
    	npServiceIntent.putExtra(STREAM_PORT_KEY, streamPort);
    	npServiceIntent.putExtra(DESIRED_DELAY_KEY, Float.valueOf(desiredDelayLabel.getText().toString()));
    	startService(npServiceIntent);
    	
    	// Bind to the NetworkPlayback Service
    	bindService(npServiceIntent, npServiceConnection, Context.BIND_AUTO_CREATE);
    	
    	// Tell INFO Service to send the "Client Listening" packet
    	if (isBoundToInfoService) {
    		infoService.sendClientListening(true, streamPort);  // true => client IS listening
    	}
    	
    	//btnStartStop.setText(R.string.stop_listening);
    	// Note: after we're successfully BOUND to the NP Service, updateGuiToReflectSystemState() will be called (and hence btnStartStop.setText("Stop Listening") will be called)
    }

	@Override
	public void onReceivingAudioStatusChanged(final boolean isReceivingAudio) {
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (isReceivingAudio) {
					lblReceivingAudio.setTextColor(Color.GREEN);
				} else {
					lblReceivingAudio.setTextColor(Color.LTGRAY);
				}
			}
		});
	}

	@Override
	public void onPlaybackSpeedStatusChanged(final int playbackSpeed) {
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateLblPlaybackSpeed(playbackSpeed);
			}
		});
	}
	
	@Override
	public void onNpServiceFinished() {
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				outputToDebugResults("NetworkPlaybackService is FINISHED");
			}
		});
	}
	
	
	private void saveGuiPrefsToFile() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DESIRED_DELAY_KEY, desiredDelayLabel.getText().toString());
		editor.commit();
	}
	
	
	private void loadGuiPrefsFromFile() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		desiredDelayLabel.setText(prefs.getString(DESIRED_DELAY_KEY, "1.0"));
	}
	
	private void outputToDebugResults(String msg) {
		debugResults.append(msg + "\n");
	}

	@Override
	public void onLppListChanged(List<LangPortPair> lppList) {
		Log.v(TAG, "onLppListChanged()");
		this.lppList.clear();
		this.lppList.addAll(lppList);
		
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// Make streamView visible if it's not already
				if (streamViewLayout.getVisibility() != View.VISIBLE) {
					waitingForServerLabel.setVisibility(View.INVISIBLE);
					streamViewLayout.setVisibility(View.VISIBLE);
				}
				
				languageSpinnerArrayAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onServerStartedStreaming() {
		Log.v(TAG, "onServerStartedStreaming()");
		// Note: we may not necessarily have lang/port pairs yet, but that should be ok. When we get them (very soon from now), the spinner will refresh.
		
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				waitingForServerLabel.setVisibility(View.INVISIBLE);
				streamViewLayout.setVisibility(View.VISIBLE);
			}
		});
	}
	
	@Override
	public void onServerStoppedStreaming() {
		Log.v(TAG, "onServerStoppedStreaming()");
		
		// Clear our lang/port pairs list, so next time we receive lpp's it'll trigger the onChanged method.
		lppList.clear();
		
		// Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				waitingForServerLabel.setVisibility(View.VISIBLE);
				streamViewLayout.setVisibility(View.INVISIBLE);
				
				// We better "click" stop listening too, if user was listening
				if (isBoundToNpService) {
					onStopListening();
				}
				
				languageSpinnerArrayAdapter.notifyDataSetChanged();
			}
		});
	}

	
	@Override
	public void onAudioFormatChanged(AudioFormatStruct af) {
		// TODO Auto-generated method stub
		// ??? Do we need this ???
	}
	
	
	private Boolean isServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
}
