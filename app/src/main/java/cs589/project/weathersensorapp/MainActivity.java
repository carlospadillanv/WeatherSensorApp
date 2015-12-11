package cs589.project.weathersensorapp;

import org.json.JSONException;

import cs589.project.weathersensorapp.model.NoiseDetector;
import cs589.project.weathersensorapp.model.Weather;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.survivingwithandroid.weatherapp.R;

public class MainActivity extends Activity {

    private static final int NOISE_POLLING_INTERVAL = 300;
    private static final int WEATHER_POLLING_INTERVAL = 5000;
    private static final int LIGHTING_DIM_THRESHOLD = 20;
    private static final int NOISE_THRESHOLD = 75;

    private String city = "London,UK";
    private EditText cityEntryField;
	private TextView cityText;

    //Weather
	private TextView mainDes;
    private TextView addedDes;
	private TextView temp;
	private TextView press;
	private TextView windSpeed;
	
	private TextView hum;
	private ImageView imgView;
    private Handler mWeatherHandler = new Handler();
    private ToggleButton togglebutton;
    private Boolean isCelsius;
    private float tempVal;
    private Button suggestionBtn;
    private int weatherGroup;

    //Location vars
	private LocationManager locationManager;
	private String provider;
    private Button cityButton;
    private Switch gpsSwitch;
    private Boolean gpsOn;
    private TextView currLat;
    private TextView currLon;
    private double lat;
    private double lon;

    //Sound Vars
    private TextView noiseDBText;
    private NoiseDetector mSensor;
    ProgressBar bar;
    private Handler mHandler = new Handler();

    //Light Vars
    private TextView lightMaxLabel;
    private TextView lightCurrentLabel;
    private TextView lightMax;
    private TextView lightCurrent;
    private RelativeLayout bgElement;

    //Accelerometer
    private long prevTime = 0;
    private float prev_x, prev_y, prev_z;
    private static final int SHAKE_THRESHOLD = 600;
    private TextView accelX;
    private TextView accelY;
    private TextView accelZ;
    private TextView accelSpeed;
    private TextView accelLabel;
    private boolean dialogOpen;

    // Create runnable thread to Monitor Noise
    private Runnable mWeatherTask = new Runnable() {
        public void run() {
            GetWeatherData();
            mWeatherHandler.postDelayed(mWeatherTask, WEATHER_POLLING_INTERVAL);
        }
    };

    // Create runnable thread to Monitor Noise
    private Runnable mPollTask = new Runnable() {
        public void run() {
            double amp = mSensor.getAmplitude();
            bar.setProgress((int)amp);

            noiseDBText.setText(String.format("%.2f", amp) + "dB");
            if ((amp > NOISE_THRESHOLD)) {
                Toast.makeText(getApplicationContext(), "Noise Detected!",
                        Toast.LENGTH_SHORT).show();
            }
            mHandler.postDelayed(mPollTask, NOISE_POLLING_INTERVAL);
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        //Location
        cityButton  = (Button) findViewById(R.id.cityBtn);
        gpsSwitch   = (Switch) findViewById(R.id.gpsSwitch);
        currLat     = (TextView) findViewById(R.id.currentLat);
        currLon     = (TextView) findViewById(R.id.currentLon);

        //Weather
        cityEntryField  = (EditText) findViewById(R.id.cityEntryField);
		cityText        = (TextView) findViewById(R.id.cityText);
		mainDes         = (TextView) findViewById(R.id.mainDes);
        addedDes        = (TextView) findViewById(R.id.addedDes);
		temp            = (TextView) findViewById(R.id.temp);
		hum             = (TextView) findViewById(R.id.hum);
		press           = (TextView) findViewById(R.id.press);
		windSpeed       = (TextView) findViewById(R.id.windSpeed);
		imgView         = (ImageView) findViewById(R.id.wIcon);
        togglebutton    = (ToggleButton) findViewById(R.id.toggleButton);
        suggestionBtn   = (Button) findViewById(R.id.suggestionBtn);

        //Sound and Light
        noiseDBText         = (TextView) findViewById(R.id.noiseDBText);
        lightMaxLabel       = (TextView) findViewById(R.id.lightSensorMaxLabel);
        lightCurrentLabel   = (TextView) findViewById((R.id.lightValLabel));
        lightMax            = (TextView) findViewById(R.id.lightSensorMaxText);
        lightCurrent        = (TextView) findViewById((R.id.lightValText));
        bgElement           = (RelativeLayout) findViewById(R.id.myLayout);

        //Accelerometer
        accelX      = (TextView) findViewById(R.id.accelX);
        accelY      = (TextView) findViewById(R.id.accelY);
        accelZ      = (TextView) findViewById((R.id.accelZ));
        accelSpeed  = (TextView) findViewById((R.id.accelSpeed));
        accelLabel  = (TextView) findViewById((R.id.accelLabel));

        weatherGroup = 0;
        dialogOpen = false;
        gpsOn = gpsSwitch.isChecked();
        isCelsius = togglebutton.isChecked();
        togglebutton.setTextOn((char) 0x00B0 + "C");
        togglebutton.setTextOff((char) 0x00B0 + "F");
        togglebutton.toggle();
        togglebutton.toggle();

        addListenerOnButton();
        addListenerOnSwitch();

        //Set up Sound
        bar=(ProgressBar)findViewById(R.id.soundMeter);
        bar.setScaleY(3f);
        bar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);

        mSensor = new NoiseDetector();

        addLightSensor();
        addAccelerometerSensor();

        GetWeatherData();
        mWeatherHandler.postDelayed(mWeatherTask, WEATHER_POLLING_INTERVAL);

	}

    /* --------------------------------------------
    Gets the Weather Data from OpenWeatherMap API
    Handles connection and JSON Parsing
    --------------------------------------------*/
    private void GetWeatherData()
    {
        // Get the location manager
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // Define the criteria how to select the locatioin provider -> use
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        android.location.Location location = locationManager.getLastKnownLocation(provider);

        //Get Weather
        JSONWeatherTask task = new JSONWeatherTask();

        if (location != null && gpsOn)
        {
            lat = (location.getLatitude());
            lon = (location.getLongitude());
            task.execute("lat=" + String.valueOf(lat) + "&lon=" + String.valueOf(lon));
        }
        else
        {
            task.execute("q=" + city);
        }
    }

    /* --------------------------------------------
       Add the light sensor.
       Make sure it is not null before using
   --------------------------------------------*/
    public void addLightSensor() {
        //Set up light sensor
        SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null){
            Toast.makeText(MainActivity.this, "No Light Sensor!", Toast.LENGTH_LONG).show();
        }
        else{
            float max =  lightSensor.getMaximumRange();
            lightMax.setText(String.valueOf(max));

            sensorManager.registerListener(allSensorEventListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

        }
    }

    /* --------------------------------------------
      Add the accelerometer sensor.
      Make sure it is not null before using
      --------------------------------------------*/
    public void addAccelerometerSensor() {
        //Set up light sensor
        SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelSensor == null){
            Toast.makeText(MainActivity.this, "Accelerometer!", Toast.LENGTH_SHORT).show();
        }
        else{
            sensorManager.registerListener(allSensorEventListener,
                    accelSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

        }
    }

    /* --------------------------------------------
      Listner for all sensor type events
      Listens to events from light sensor and
      accelerometer.
      --------------------------------------------*/
    SensorEventListener allSensorEventListener = new SensorEventListener(){

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Get light sensor data
            if(event.sensor.getType()==Sensor.TYPE_LIGHT){
                final float currentReading = event.values[0];
                lightCurrent.setText(String.valueOf(currentReading));

                if (currentReading < LIGHTING_DIM_THRESHOLD)
                {
                    viewMode(Color.BLACK, Color.WHITE);
                }
                else
                {
                    viewMode(Color.WHITE, Color.BLACK);
                }
            }
            else if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                accelX.setText(String.format("X: %.2f", x));
                accelY.setText(String.format("Y: %.2f", y));
                accelZ.setText(String.format("Z: %.2f", z));
                accelSpeed.setText(String.format("Spd: %.2f", z));

                long curTime = System.currentTimeMillis();

                if ((curTime - prevTime) > 100) {
                    long diffTime = (curTime - prevTime);
                    prevTime = curTime;

                    float speed = Math.abs(x + y + z - prev_x - prev_y - prev_z)/ diffTime * 10000;

                    if (speed > SHAKE_THRESHOLD) {
                        //Toast.makeText(MainActivity.this, "Earthquake!", Toast.LENGTH_SHORT).show();
                        showAlertDialog("Earthquake!", "Find cover!");
                    }

                    prev_x = x;
                    prev_y = y;
                    prev_z = z;
                }

            }
        }
    };

    /* --------------------------------------------
     Add on click listeners for buttons
    --------------------------------------------*/
    public void addListenerOnButton() {
        cityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                city = cityEntryField.getText().toString();
            }
        });

        suggestionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                weatherSuggestion();
            }
        });
    }

    /* --------------------------------------------
     Add on click listeners for switches
    --------------------------------------------*/
    public void addListenerOnSwitch(){
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                gpsOn = isChecked;
            }
        });
    }

    /* --------------------------------------------
    On click function for toggle switch
    --------------------------------------------*/
    public void toggleclick(View v){
        isCelsius = togglebutton.isChecked();
    }

    /* --------------------------------------------
     Display Alert Dialog with custome message
    --------------------------------------------*/
    public void showAlertDialog(String title, String message)
    {
        if( dialogOpen == false) {
            dialogOpen = true;
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set title
            alertDialogBuilder.setTitle(title);

            // set dialog message
            alertDialogBuilder
                    .setMessage(message)
                    .setCancelable(false)
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialogOpen = false;
                            dialog.cancel();
                        }
                    });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
    }

    /* --------------------------------------------
    Change Background and Font color
    (used with light detection)
    --------------------------------------------*/
    protected void viewMode(int bkgdColor, int fontColor) {
        bgElement.setBackgroundColor(bkgdColor);
        lightCurrentLabel.setTextColor(fontColor);
        lightMaxLabel.setTextColor(fontColor);
        lightCurrent.setTextColor(fontColor);
        lightMax.setTextColor(fontColor);
        cityText.setTextColor(fontColor);
        mainDes.setTextColor(fontColor);
        addedDes.setTextColor(fontColor);
        noiseDBText.setTextColor(fontColor);
        cityButton.setTextColor(fontColor);
        currLat.setTextColor(fontColor);
        currLon.setTextColor(fontColor);
        accelX.setTextColor(fontColor);
        accelY.setTextColor(fontColor);
        accelZ.setTextColor(fontColor);
        accelSpeed.setTextColor(fontColor);
        accelLabel.setTextColor(fontColor);
        gpsSwitch.setTextColor(fontColor);
    }
    @Override
    public void onResume() {
        super.onResume();
        //Start using mic sensor
        mSensor.start();
        mHandler.postDelayed(mPollTask, NOISE_POLLING_INTERVAL);
    }
    @Override
    public void onStop() {
        super.onStop();
        //Release mic sensor
        mSensor.stop();
        mHandler.removeCallbacks(mPollTask);
    }

    /* --------------------------------------------
    Provide suggestion based on current weather
    conditions
    --------------------------------------------*/
    protected void weatherSuggestion() {

        if (weatherGroup == Weather.THUNDERSTORM) {
            showAlertDialog("Thunder!", "Postpone outdoor activities.");
        }
        else if ((weatherGroup == Weather.DRIZZLE)||
                 (weatherGroup == Weather.RAIN)
                )
        {
            showAlertDialog("Rain!", "Bring an umbrella");
        }
        else if (weatherGroup == Weather.SNOW){
            showAlertDialog("Snow", "Wear a winter coat, use tire chains");
        }
        else if (weatherGroup == Weather.ATMOSPHERE) {
            showAlertDialog("Atmospheric Conditions", "Use fog lights!");
        }
        else if (weatherGroup == Weather.CLEAR) {
            if(tempVal >= 85)
            {
                showAlertDialog("Hot Sun", "Wear Sunscreen!");
            }
            else if(tempVal >= 80 && tempVal < 85)
            {
                showAlertDialog("Warm Day", "Go to the beach!");
            }
        }
        else if ((weatherGroup == Weather.PARTLY_CLOUDY) ||
                 (weatherGroup == Weather.CLOUDS)
                )
        {
            showAlertDialog("Cloudy Day", "Good weather for a run!");
        }
        else if (weatherGroup == Weather.EXTREME) {
            showAlertDialog("Extreme Conditions", "Stay inside!");
        }

    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
    }

    /* --------------------------------------------
    Task used to get Weather data from
    OpenWeatherMap API
    --------------------------------------------*/
    private class JSONWeatherTask extends AsyncTask<String, Void, Weather> {
		
		@Override
		protected Weather doInBackground(String... params) {
			Weather weather = new Weather();
			String data = ( (new WeatherHttpClient()).getWeatherData(params[0]));

			try {
				weather = JSONWeatherParser.getWeather(data);

			} catch (JSONException e) {
				e.printStackTrace();
			}
			return weather;

	}

	/* --------------------------------------------
    Executes after Weather Data is retrieved
    --------------------------------------------*/
	@Override
		protected void onPostExecute(Weather weather) {
			super.onPostExecute(weather);

            if(weather != null &&
               weather.location != null &&
               weather.currentCondition != null &&
               weather.wind != null
              )
            {
                weatherGroup = weather.currentCondition.getWeatherGroup();
                setIcon(weatherGroup);
                setTemp(weather);

                cityText.setText(weather.location.getCity() + "," + weather.location.getCountry());
                mainDes.setText(weather.currentCondition.getCondition());
                addedDes.setText(weather.currentCondition.getDescr());
                hum.setText("" + weather.currentCondition.getHumidity() + "%");
                press.setText("" + weather.currentCondition.getPressure() + " hPa");
                windSpeed.setText("" + weather.wind.getSpeed() + " mps");
                currLat.setText(String.format("lat: %.2f", lat));
                currLon.setText(String.format("lon: %.2f", lon));
            }
		}

        /* --------------------------------------------
        Set Weather Temp (Celcius or Fahrenheit)
        --------------------------------------------*/
        protected void setTemp(Weather weather)
        {
            //Fahrenheit temp used for checks
            tempVal = Math.round(((weather.temperature.getTemp() - 273.15)* 1.8) + 32);
            if(isCelsius) {
                temp.setText("" + Math.round((weather.temperature.getTemp() - 273.15)) + (char) 0x00B0 + "C");
            }
            else {

                temp.setText("" + Math.round(((weather.temperature.getTemp() - 273.15)* 1.8) + 32) + (char) 0x00B0 + "F");
            }
        }

        /* --------------------------------------------
        Determine Icon to display
        --------------------------------------------*/
        protected void setIcon(int weatherGroup) {

            if (weatherGroup == Weather.THUNDERSTORM) {
                imgView.setImageResource(R.drawable.thunder);
            }
            else if (weatherGroup == Weather.DRIZZLE) {
                imgView.setImageResource(R.drawable.rain);
            }
            else if (weatherGroup == Weather.RAIN) {
                imgView.setImageResource(R.drawable.rain);
            }
            else if (weatherGroup == Weather.SNOW){
                imgView.setImageResource(R.drawable.snow);
            }
            else if (weatherGroup == Weather.ATMOSPHERE) {
                imgView.setImageResource(R.drawable.fog);
            }
            else if (weatherGroup == Weather.CLEAR) {
                imgView.setImageResource(R.drawable.sunny);
            }
            else if (weatherGroup == Weather.PARTLY_CLOUDY) {
                imgView.setImageResource(R.drawable.partly_cloudy);
            }
            else if (weatherGroup == Weather.CLOUDS) {
                imgView.setImageResource(R.drawable.cloudy);
            }
            else if (weatherGroup == Weather.EXTREME) {
                imgView.setImageResource(R.drawable.weather_clips);
            }
            else if (weatherGroup == Weather.ADDITONAL) {
                imgView.setImageResource(R.drawable.weathers);
            }
            else {
                imgView.setImageResource(R.drawable.weathers);
            }
        }
  }
}
