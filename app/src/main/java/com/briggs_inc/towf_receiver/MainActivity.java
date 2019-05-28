package com.briggs_inc.towf_receiver;


import java.util.ArrayList;
import java.util.List;
import com.briggs_inc.towf_receiver.InfoService.InfoServiceBinder;
import com.briggs_inc.towf_receiver.NetworkPlaybackService.NpServiceBinder;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import static com.briggs_inc.towf_receiver.MiscConstants.*;


public class MainActivity extends AppCompatActivity implements NetworkPlaybackServiceListener, InfoServiceListener {

    private static final String TAG = "MainActivity";
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;
	private static final int MY_PERMISSIONS_REQUEST_FG_SERVICE = 2;

    // GUI-related
	TextView wifiConnection;
    TextView versionLabel;
	TextView waitingForServerLabel;
	LinearLayout streamViewLayout;
	LinearLayout listeningViewLayout;
    Button btnStartStop;
    Spinner languageSpinner;
    SeekBar desiredDelaySeekBar;
    TextView desiredDelayLabel;
    TextView lblReceivingAudio;
    TextView lblPlaybackSpeed;
    ToggleButton sendMissingPacketRequestsTB;
    EditText chatMsgTF;
    Button sendChatMsgBtn;
    TextView debugResults;
    
    
    //DatagramSocket infoSocket;
    //ArrayList<LangPortPair> lppArrayList;

    ArrayAdapter<LangPortPair> languageSpinnerArrayAdapter;
    
    List<LangPortPair> lppList = new ArrayList<>();
    
    Intent npServiceIntent;
    Intent infoServiceIntent;
    
    NetworkPlaybackService npService;
    Boolean isBoundToNpService = false;
    
    InfoService infoService;
    Boolean isBoundToInfoService = false;

    int streamPort;

    // Drip
    private SoundPool dripSoundPool;
    private int dripSound;
    boolean dripLoaded = false;

    String appVersionStr = "0.0";
    
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

            if (npService.getStreamPort() != 0) {
                streamPort = npService.getStreamPort();
            }

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
        versionLabel = (TextView) findViewById(R.id.versionLabel);
        waitingForServerLabel = (TextView) findViewById(R.id.waitingForServerLabel);
        streamViewLayout = (LinearLayout) findViewById(R.id.streamViewLayout);
        listeningViewLayout = (LinearLayout) findViewById(R.id.listeningViewLayout);
        btnStartStop = (Button) findViewById(R.id.btnStartStop);
        languageSpinner = (Spinner) findViewById(R.id.languageSpinner);
        desiredDelaySeekBar = (SeekBar) findViewById(R.id.desiredDelaySeekBar);
        desiredDelayLabel = (TextView) findViewById(R.id.desiredDelayLabel);
        lblReceivingAudio = (TextView) findViewById(R.id.lblReceivingAudio);
        lblPlaybackSpeed = (TextView) findViewById(R.id.lblPlaybackSpeed);
        sendMissingPacketRequestsTB = (ToggleButton) findViewById(R.id.sendMissingPacketRequestsTB);
        chatMsgTF = (EditText) findViewById(R.id.chatMsgTF);
        sendChatMsgBtn = (Button) findViewById(R.id.sendChatMsgBtn);
        debugResults = (TextView) findViewById(R.id.debugResults);
        
        loadGuiPrefsFromFile();
        
        // Language Spinner Array Adapter
        languageSpinnerArrayAdapter = new ArrayAdapter<LangPortPair>(this, android.R.layout.simple_spinner_item, lppList);
        languageSpinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageSpinnerArrayAdapter);
        
        npServiceIntent = new Intent(this, NetworkPlaybackService.class);
        infoServiceIntent = new Intent(this, InfoService.class);

		// Permissions
		if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(MainActivity.this,
					new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
					MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
		}
		if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.v(TAG, "NO PERMISSION FOR READ_CONTACTS");
			ActivityCompat.requestPermissions(MainActivity.this,
					new String[]{ Manifest.permission.READ_CONTACTS },
					MY_PERMISSIONS_REQUEST_READ_CONTACTS);
		}
		if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.FOREGROUND_SERVICE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.v(TAG, "NO PERMISSION FOR FOREGROUND_SERVICE");
			ActivityCompat.requestPermissions(MainActivity.this,
					new String[]{ Manifest.permission.FOREGROUND_SERVICE },
					MY_PERMISSIONS_REQUEST_FG_SERVICE);
		}

        sendMissingPacketRequestsTB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isBoundToNpService) {
					npService.setSendMissingPacketRequestsChecked(isChecked);
				}
            }
        });
        chatMsgTF.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;

                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    processOutgoingChatMsg();
                    handled = true;
                }

                return handled;
            }
        });
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
        	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(infoServiceIntent);
			} else {
				ContextCompat.startForegroundService(this, infoServiceIntent);
			}
        }

        // Setup Drip Sound
        //this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Load the sound
        buildSoundPool();
        dripSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                dripLoaded = true;
            }
        });
        dripSound = dripSoundPool.load(this, R.raw.drip, 1);

        // Get App Version String (e.g: 3.0)
        try {
            appVersionStr = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            appVersionStr = "0.0";
        }

        // Set Version text in GUI
        versionLabel.setText("(v" + appVersionStr + ")");
    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch (requestCode) {
			case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.v(TAG, "*************** PERMISSION GRANTED for ACCESS_FINE_LOCATION! ************");
				} else {
					Log.v(TAG, "**************** NO PERMISSION for ACCESS_FINE_LOCATION! ************");
				}
				return;
			}
			case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.v(TAG, "*********** PERMISSION GRANTED for READ_CONTACTS! **********");
				} else {
					Log.v(TAG, "************ NO PERMISSION for READ_CONTACTS!! ************");
				}
				return;
			}
			case MY_PERMISSIONS_REQUEST_FG_SERVICE: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.v(TAG, "*********** PERMISSION GRANTED for FG_SERVICE! **********");
				} else {
					Log.v(TAG, "************ NO PERMISSION for FG_SERVICE!! ************");
				}
				return;
			}
		}
	}
	
	private void updateLblPlaybackSpeed(int playbackSpeed) {
		if (playbackSpeed == PlaybackManager.PLAYBACK_SPEED_FASTER) {
			lblPlaybackSpeed.setText(String.format("%1.1fx", PlaybackManager.FASTER_PLAYBACK_MULTIPLIER));
			lblPlaybackSpeed.setTextColor(Color.BLUE);
		} else if (playbackSpeed == PlaybackManager.PLAYBACK_SPEED_SLOWER) {
            lblPlaybackSpeed.setText(String.format("%1.1fx", PlaybackManager.SLOWER_PLAYBACK_MULTIPLIER));
            lblPlaybackSpeed.setTextColor(Color.rgb(0xB0, 0xB0, 0x00));  // Yellow
        } else if (playbackSpeed == PlaybackManager.PLAYBACK_SPEED_SUPER_FAST) {
            lblPlaybackSpeed.setText("Fast");
            lblPlaybackSpeed.setTextColor(Color.RED);
		} else {
			lblPlaybackSpeed.setText("1x");
			lblPlaybackSpeed.setTextColor(Color.rgb(0x00, 0x80, 0x00));
		}		
	}

	private void updateLblReceivingAudio(Boolean isReceivingAudio) {
		if (isReceivingAudio) {
			lblReceivingAudio.setTextColor(Color.rgb(0x00, 0x80, 0x00));  // Green
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
        if (Build.FINGERPRINT.contains("generic")) {
            wifiConnection.setText("Tercume");  // Just to make screenshots look nicer from the Emulator.
        } else {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            wifiConnection.setText(wifiInfo.getSSID());
        }

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
    	Log.v(TAG, "onPause() ");
    	super.onPause();
    }
    
    @Override
    protected void onStop() {
    	Log.v(TAG, "onStop()");
    	super.onStop();
    	
    	// NetPlay Service (Unbind)
/*    	if (isBoundToNpService) {
    		npService.removeListener(this);
    		Log.v(TAG, "unbinding NetPlayService");
    		unbindService(npServiceConnection);
    		npService = null;  // ???
    		isBoundToNpService = false;
    	}

    	//INFO Service (Unbind)
    	if (isBoundToInfoService) {
    		infoService.removeListener(this);
			Log.v(TAG, "unbinding InfoService");
    		unbindService(infoServiceConnection);
    		infoService = null;  // ???
    		isBoundToInfoService = false;
    	}
*/    	Log.v(TAG, "done with onStop()");
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
        if (isBoundToInfoService) {  // should always be bound at this point in the code, but just to make sure
            npServiceIntent.putExtra(AF_SAMPLE_RATE_KEY, infoService.getAfSampleRate());
        }
        npServiceIntent.putExtra(SEND_MPRS_ENABLED_KEY, sendMissingPacketRequestsTB.isChecked());
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(npServiceIntent);
		} else {
			ContextCompat.startForegroundService(this, npServiceIntent);
		}
    	
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
					lblReceivingAudio.setTextColor(Color.rgb(0x00, 0x80, 0x00));
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
		/* KEEP!!! for future use
		//   but deciding not to store desiredDelay, in case user sets it too low & maybe causes crash on device. If saved, then no easy way to set the delay higher.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DESIRED_DELAY_KEY, desiredDelayLabel.getText().toString());
		editor.commit();
		*/
	}
	
	
	private void loadGuiPrefsFromFile() {
		/* KEEP!!! for future use
		//   but deciding not to store desiredDelay, in case user sets it too low & maybe causes crash on device. If saved, then no easy way to set the delay higher.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		desiredDelayLabel.setText(prefs.getString(DESIRED_DELAY_KEY, "1.0"));
		*/
	}
	
	private void outputToDebugResults(String msg) {
		debugResults.append(msg + "\n");
	}

    @Override
    public void onServerVersionChanged(final String serverVersion) {
        Log.v(TAG, "onServerVersionChanged(): " + serverVersion);

        // Check that received Server's version matches our version (only check the first number). If not, alert the user.
        String serverMajorVer = serverVersion.split("\\.")[0];
        String appMajorVer = appVersionStr.split("\\.")[0];
        Log.d(TAG, "serverMajorVer: " + serverMajorVer);
        Log.d(TAG, "appMajorVer: " + appMajorVer);
        final String todoMsg;


        if (!serverMajorVer.equalsIgnoreCase(appMajorVer)) {
            if (Integer.valueOf(appMajorVer) < Integer.valueOf(serverMajorVer)) {
                todoMsg = "You need to update this app to the latest version";
            } else {
                todoMsg = "The Server software must be updated to the latest version";
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Versions not Compatible!")
                            .setMessage(String.format("This App's Version (%s) and the Server's version (%s) are not compatible. The Major version (1st number) must be the same.\n\n%s", appVersionStr, serverVersion, todoMsg))
                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing.
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert);


                    AlertDialog alert = builder.create();
                    alert.show();
                }
            });
        }
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
    public void onAfSampleRateChanged(final int sampleRate) {
        Log.v(TAG, "onAfSampleRateChanged: " + sampleRate);

        if(npService!=null&&isBoundToNpService) {
            Log.v(TAG, "npService is NOT null. Telling npServer about afSampleRate change...");
            npService.onAfSampleRateChanged(sampleRate);
        }
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

    public void onSendChatMsgClicked(View v) {
        Log.v(TAG, "onSendChatMsgClicked");

        processOutgoingChatMsg();

    }

    private void processOutgoingChatMsg() {
        String msg = chatMsgTF.getText().toString();

        if (!msg.equalsIgnoreCase("")) {
            chatMsgTF.setText("");  // Clear textedit
            debugResults.setText(debugResults.getText() + "Me: " + msg + "\n");  // Show msg in the debugResults view.

            // Send msg to Server
            infoService.sendChatMsg(msg);

            // Beep
            playDripSound();
        }

		// Hide soft keyboard when "Send" (button or keyboard key) is clicked
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(chatMsgTF.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
    }

    @Override
    public void onChatMsgReceived(final String msg) {
        // Note: this comes from a thread (other than the UI thread) - and we can't update UI elements from some other thread. So we need to runOnUiThread()
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                debugResults.setText(debugResults.getText().toString() + "Server: " + msg + "\n");
            }
        });
        // Beep (drip)
        playDripSound();
    }

    @Override
    public void onRLSReceived() {
        if (isServiceRunning(NetworkPlaybackService.class)) {
            infoService.sendClientListening(true, streamPort);  // We're listening
        } else {
            infoService.sendClientListening(false, streamPort);  // We're not listening
        }
    }

    @Override
    public void onMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPackets) {
        //Log.v(TAG, "onMissingPacketRequestCreated");
        if (isBoundToInfoService) {
            infoService.sendMissingPacketRequest(streamPort, missingPackets);
        }
    }

    @Override
    public void onDisableMPRSwitch() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendMissingPacketRequestsTB.setChecked(false);
                sendMissingPacketRequestsTB.setEnabled(false);
            }
        });
    }

    @Override
    public void onEnableMPRSwitch() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendMissingPacketRequestsTB.setEnabled(true);
            }
        });
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void buildSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes driopAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            dripSoundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(driopAttributes)
                    .build();
        } else {
            dripSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        }
    }

    public void playDripSound() {
        if (dripLoaded) {
            dripSoundPool.play(dripSound, 1, 1, 1, 0, 1f);
        }
    }
}
