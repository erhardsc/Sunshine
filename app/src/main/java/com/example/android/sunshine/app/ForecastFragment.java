package com.example.android.sunshine.app;

//import android.app.Fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by sumnererhard on 12/27/16.
 */
public class ForecastFragment extends Fragment {
    private final String LOG_TAG = ForecastFragment.class.getSimpleName();
    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    private void updateWeather(){
        fetchWeatherTask weatherTask = new fetchWeatherTask();
        String location = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(getString(R.string.pref_location_key),
                        getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); //allows the fragment to handle menu events
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Create some dummy data for the ListView.  Here's a sample weekly forecast
        String[] data = {
                "Mon 6/23 - Sunny - 31/17",
                "Tue 6/24 - Foggy - 21/8",
                "Wed 6/25 - Cloudy - 22/17",
                "Thurs 6/26 - Rainy - 18/11",
                "Fri 6/27 - Foggy - 21/10",
                "Sat 6/28 - TRAPPED IN WEATHERSTATION - 23/18",
                "Sun 6/29 - Sunny - 20/7"
        };

        List<String> weekForecast = new ArrayList<>(Arrays.asList(data));


        // Now that we have some dummy forecast data, create an ArrayAdapter.
        // The ArrayAdapter will take data from a source (like our dummy forecast) and
        // use it to populate the ListView it's attached to.
        mForecastAdapter =
                new ArrayAdapter<>(
                        getActivity(), // The current context (this activity)
                        R.layout.list_item_forecast, // The name of the layout ID.
                        R.id.list_item_forecast_textview, // The ID of the textview to populate.
                        new ArrayList<String>()); //pass empty array list. pass weekForecast for static data

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);


                Intent intent = new Intent(getActivity(), DetailActivity.class);
                intent.putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);

                //Toast.makeText(getActivity(), getActivity().toString(), Toast.LENGTH_LONG).show();
            }
        });

        return rootView;
    }



        public class fetchWeatherTask extends AsyncTask<String, Void, String[]> {

            private final String LOG_TAG = fetchWeatherTask.class.getSimpleName();

            // Convert date time from JSON string
            private String getReadableDateString(long time){
                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
                return shortenedDateFormat.format(time);
            }
            // Prepare weather high and lows

            private String formatHighLows(double high, double low, String unitType) {

                if (unitType.equals(getString(R.string.pref_units_imperial))) {
                    high = (high * 1.8) + 32;
                    low = (low * 1.8) + 32;
                } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
                    Log.d(LOG_TAG, "Unit type not found: " + unitType);
                }

                // For presentation, assume the user doesn't care about tenths of a degree.
                long roundedHigh = Math.round(high);
                long roundedLow = Math.round(low);

                String highLowStr = roundedHigh + "/" + roundedLow;
                return highLowStr;
            }

            private String[] getWeatherDateFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

                final String OWM_LIST = "list";
                final String OWM_WEATHER = "weather";
                final String OWM_TEMPERATURE = "temp";
                final String OWM_MAX = "max";
                final String OWM_MIN = "min";
                final String OWM_DESCRIPTION = "main";

                JSONObject forecastJson = new JSONObject(forecastJsonStr);
                JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

                Time dayTime = new Time();
                dayTime.setToNow();

                SharedPreferences sharedPrefs =
                        PreferenceManager.getDefaultSharedPreferences(getActivity());
                String unitType = sharedPrefs.getString(
                        getString(R.string.pref_units_key),
                        getString(R.string.pref_units_metric));

                int startDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

                dayTime = new Time();

                String[] resultStrs = new String[numDays];

                for(int i = 0; i < weatherArray.length(); i++) {
                    // For now, using the format "Day, description, hi/low"
                    String day;
                    String description;
                    String highAndLow;


                    // Get the JSON object representing the day
                    JSONObject dayForecast = weatherArray.getJSONObject(i);

                    // The date/time is returned as a long.  We need to convert that
                    // into something human-readable, since most people won't read "1400356800" as
                    // "this saturday".
                    long dateTime;
                    // Cheating to convert this to UTC time, which is what we want anyhow
                    dateTime = dayTime.setJulianDay(startDay+i);
                    day = getReadableDateString(dateTime);

                    // description is in a child array called "weather", which is 1 element long.
                    JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                    description = weatherObject.getString(OWM_DESCRIPTION);

                    // Temperatures are in a child object called "temp".  Try not to name variables
                    // "temp" when working with temperature.  It confuses everybody.
                    JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                    double high = temperatureObject.getDouble(OWM_MAX);
                    double low = temperatureObject.getDouble(OWM_MIN);

                    highAndLow = formatHighLows(high, low, unitType);
                    resultStrs[i] = day + " | " + description + " | " + highAndLow;
                }
                //for (String s : resultStrs) {
                    //Log.v(LOG_TAG, "Forecast entry: " + s);
               // }
                return resultStrs;

            }

            @Override
            protected String[] doInBackground(String... params) {

                if (params.length == 0){
                    return null;
                }

                HttpURLConnection urlConnection = null;
                BufferedReader reader = null;
                String forecastJsonStr = null;
                String format = "JSON";
                String units = "metric";
                int numDays = 7;

                try {

                    final String BASE_URL =
                            "http://api.openweathermap.org/data/2.5/forecast/daily?";
                    final String QUERY_PARAM = "q";
                    final String FORMAT_PARAM = "mode";
                    final String UNITS_PARAM = "units";
                    final String DAYS_PARAM = "cnt";
                    final String API_KEY = "APPID";

                    Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                            .appendQueryParameter(QUERY_PARAM, params[0])
                            .appendQueryParameter(FORMAT_PARAM, format)
                            .appendQueryParameter(UNITS_PARAM, units)
                            .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                            .appendQueryParameter(API_KEY, getString(R.string.Weather_API))
                            .build();

                    URL url = new URL(builtUri.toString());

                    //Log.v(LOG_TAG, "Built URI " + builtUri.toString());

                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.connect();

                    InputStream inputStream = urlConnection.getInputStream();
                    StringBuffer buffer = new StringBuffer();
                    if (inputStream == null){
                        return null;
                    }
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line + "\n");
                    }

                    if (buffer.length() == 0){
                        return null;
                    }

                    forecastJsonStr = buffer.toString();
                    //Log.v(LOG_TAG, "Forecast JSON String: " + forecastJsonStr);

                } catch(IOException e) {
                    Log.e(LOG_TAG, "ERROR ", e);
                    return null;

                } finally{
                    if (urlConnection != null){
                        urlConnection.disconnect();
                    }
                    if (reader != null){
                        try {
                            reader.close();
                        }catch (final IOException e){
                            Log.e(LOG_TAG, "ERROR closing stream ", e);
                        }
                    }
                }
                try{
                    return getWeatherDateFromJson(forecastJsonStr, numDays);
                }catch (JSONException e){
                    Log.e(LOG_TAG, e.getMessage(), e);
                    e.printStackTrace();
                }
               return null;
            }

            @Override
            protected void onPostExecute(String[] result){
                if (result != null){
                    mForecastAdapter.clear();
                    for(String dayForecastStr : result){
                        mForecastAdapter.add(dayForecastStr);
                    }
                }
            }
        }
}
