package com.example.rcjp.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *  Main activity displaying tiles of movie posters
 */
public class MainActivityFragment extends Fragment {
    ArrayList<String> mPosterLocations;  // list of all movie poster image http locations
    // set by FetchMovieInfoTask or parcel
    ImageAdapter mImageAdapter;          // used for background Async task to notify data changes
    private String mMovieJSONStr;
    private static String mSortby;              // used to keep the current movie sort order

    public MainActivityFragment() {
        mPosterLocations = new ArrayList<>();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // extra things to save to bundle when destroying
        outState.putStringArrayList("movies", mPosterLocations);
        outState.putString("moviesJSON", mMovieJSONStr);
        super.onSaveInstanceState(outState);
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void onStart() {
        super.onStart();
        //
        // only way I know of checking the sort preferences have changed in the
        // SettingsActivity is to check here in onStart which gets called when coming back
        // from the settings page, hmmm hope there is a better way to do this
        //
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sortby = sharedPrefs.getString(
                getString(R.string.pref_sort_key),
                getString(R.string.pref_sort_popular));

        if (!sortby.equals(mSortby)) {
            // sort order has changed, need to re-get the movie data from server
            FetchMovieInfoTask fetchMovieInfoTask = new FetchMovieInfoTask();
            fetchMovieInfoTask.execute();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        GridView gridview = (GridView) rootView.findViewById(R.id.grid_view);
        mImageAdapter = new ImageAdapter(getActivity());
        gridview.setAdapter(mImageAdapter);
        gridview.setClickable(true);

        gridview.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {

                try {
                    Intent intent = new Intent(getActivity(), DetailActivity.class);
                    intent.putExtra("title", getMovieInfoStringFromJSON(mMovieJSONStr, "original_title", position));
                    intent.putExtra("date", getMovieInfoStringFromJSON(mMovieJSONStr, "release_date", position));
                    intent.putExtra("rating", getMovieRatingFromJSON(mMovieJSONStr, position));
                    intent.putExtra("synopsis", getMovieInfoStringFromJSON(mMovieJSONStr, "overview", position));
                    intent.putExtra("imageURL", getMoviePostersURL(position));

                    startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // recover the poster locations and moviedb info if just rotating device
        if (savedInstanceState == null
                || !savedInstanceState.containsKey("movies")
                || !savedInstanceState.containsKey("moviesJSON")) {
            FetchMovieInfoTask fetchMovieInfoTask = new FetchMovieInfoTask();
            fetchMovieInfoTask.execute();
        } else {
            mPosterLocations = savedInstanceState.getStringArrayList("movies");
            mMovieJSONStr = savedInstanceState.getString("moviesJSON");
        }

        return rootView;
    }


    public class ImageAdapter extends BaseAdapter {
        private Context mContext;
        private final String LOG_TAG = ImageAdapter.class.getSimpleName();

        public ImageAdapter(Context c) {
            mContext = c;
        }

        public long getItemId(int position) {
            return 0;
        }

        public Object getItem(int position) {
            return null;
        }

        public int getCount() {
            return mPosterLocations.size();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                // if it's not recycled, initialize some attributes
                imageView = new ImageView(mContext);
                imageView.setAdjustViewBounds(true);   // need this to make images tile
            } else {
                imageView = (ImageView) convertView;
            }

            String imageURLStr = getMoviePostersURL(position);

            // Use Picasso to cache load the images for movies
            try {
                Picasso.with(mContext).load(imageURLStr).into(imageView);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error loading image from:" + imageURLStr);
                e.printStackTrace();
            }
            return imageView;
        }
    }

    public class FetchMovieInfoTask extends AsyncTask<Void, Void, String[]> {
        private final String LOG_TAG = FetchMovieInfoTask.class.getSimpleName();

        protected String[] doInBackground(Void... nothing) {
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String sortby = sharedPrefs.getString(
                    getString(R.string.pref_sort_key),
                    getString(R.string.pref_sort_popular));
            mSortby = sortby; // keep a record of what order we last fetched the data in

            // README
            // ======
            // R.string.moviedbAPIKEY is a private string unique to each developer available from www.themoviedb.org
            // To build this project create a project string resource called private_string.xml as
            //<resources>
            //<string name="moviedbAPIKEY">"..insert your API key here.."</string>
            //</resources>
            try {
                Uri.Builder uri = new Uri.Builder();
                uri.scheme("http")
                        .authority("api.themoviedb.org")
                        .appendPath("3")
                        .appendPath("discover")
                        .appendPath("movie")
                        .appendQueryParameter("sort_by", sortby)
                        .appendQueryParameter("api_key", getActivity().getString(R.string.moviedbAPIKEY))
                        .build();

                URL moviedbURL = new URL(uri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) moviedbURL.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    mMovieJSONStr = null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    mMovieJSONStr = null;
                }
                mMovieJSONStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            // Parse the movie database JSON strings to pull out the http image locations
            try {
                return getMoviePostersFromJSON(mMovieJSONStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            if (strings != null) {
                mPosterLocations.clear();
                mPosterLocations.addAll(Arrays.asList(strings));

                mImageAdapter.notifyDataSetChanged();
            }
            super.onPostExecute(strings);
        }

    }

    private String[] getMoviePostersFromJSON(String movieJSONStr) throws JSONException {
        // These are the names of the JSON objects that need to be extracted.
        final String MOVIEDB_RESULTS = "results";
        final String MOVIEDB_POSTER = "poster_path";

        JSONArray movieArray = new JSONObject(movieJSONStr).getJSONArray(MOVIEDB_RESULTS);

        String[] resultStrs = new String[movieArray.length()];
        for (int i = 0; i < movieArray.length(); i++) {
            JSONObject movieJSON = movieArray.getJSONObject(i);
            resultStrs[i] = movieJSON.getString(MOVIEDB_POSTER);
        }
        return resultStrs;
    }

    private String getMovieInfoStringFromJSON(String movieJSONStr, String info, int position) throws JSONException {
        // These are the names of the JSON objects that need to be extracted.
        final String MOVIEDB_RESULTS = "results";

        JSONArray movieArray = new JSONObject(movieJSONStr).getJSONArray(MOVIEDB_RESULTS);

        String result = "";
        if (position < movieArray.length()) {
            JSONObject movieJSON = movieArray.getJSONObject(position);
            result = movieJSON.getString(info);
        }

        return result;
    }

    private double getMovieRatingFromJSON(String movieJSONStr, int position) throws JSONException {
        // These are the names of the JSON objects that need to be extracted.
        final String MOVIEDB_RESULTS = "results";
        final String MOVIEDB_RATING = "vote_average";

        JSONArray movieArray = new JSONObject(movieJSONStr).getJSONArray(MOVIEDB_RESULTS);

        double rating = 0.0;
        if (position < movieArray.length()) {
            JSONObject movieJSON = movieArray.getJSONObject(position);
            rating = movieJSON.getDouble(MOVIEDB_RATING);
        }

        return rating;
    }

    private String getMoviePostersURL(int position) {
        // Construct URL for images from the moview database
        Uri.Builder uri = new Uri.Builder();
        uri.scheme("http")
                .authority("image.tmdb.org")
                .appendPath("t")
                .appendPath("p")
                .appendPath("w185")
                .appendEncodedPath(mPosterLocations.get(position))
                .build();

        return uri.toString();
    }
}