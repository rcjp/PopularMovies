package com.example.rcjp.popularmovies;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {
    ArrayList<String> mPosterLocations;
    ImageAdapter imageAdapter;

    public MainActivityFragment() {
        mPosterLocations = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        GridView gridview = (GridView) rootView.findViewById(R.id.grid_view);
        imageAdapter = new ImageAdapter(getActivity());
        gridview.setAdapter(imageAdapter);

        FetchMovieInfoTask fetchMovieInfoTask = new FetchMovieInfoTask();

        fetchMovieInfoTask.execute("test"); //todo remove string
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
//                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                imageView = (ImageView) convertView;
            }

            // Construct URL for images from the moview database
            Uri.Builder uri = new Uri.Builder();
            uri.scheme("http")
                    .authority("image.tmdb.org")
                    .appendPath("t")
                    .appendPath("p")
                    .appendPath("w185")
                    .appendEncodedPath(mPosterLocations.get(position))
                    .build();
            String imageURLStr = uri.toString();

            // Use Picasso to cache load the images for movies
            try {
                Picasso.with(mContext).load(imageURLStr).into(imageView);
            } catch (Exception e) {
                Log.e (LOG_TAG,"Error loading image from:"+imageURLStr);
                e.printStackTrace();
            }
            return imageView;
        }
    }

    public class FetchMovieInfoTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchMovieInfoTask.class.getSimpleName();

        /*public FetchWeatherTask(Activity myActivity){
            context = myActivity;
        }*/

        protected String[] doInBackground(String... postcodes) {
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String movieJsonStr = null;

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
                        .appendQueryParameter("sort_by", "popularity.desc")
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
                    movieJsonStr = null;
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
                    movieJsonStr = null;
                }
                movieJsonStr = buffer.toString();
                Log.v(LOG_TAG, "Movie JSON:" + movieJsonStr);

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
                return getMoviePostersFromJSON(movieJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        private String[] getMoviePostersFromJSON(String movieJSONStr) throws JSONException {
            // These are the names of the JSON objects that need to be extracted.
            final String MOVIEDB_RESULTS = "results";
            final String MOVIEDB_POSTER = "poster_path";

            JSONObject moviesJSON = new JSONObject(movieJSONStr);
            JSONArray movieArray = moviesJSON.getJSONArray(MOVIEDB_RESULTS);

            String[] resultStrs = new String[movieArray.length()];
            for(int i = 0; i < movieArray.length(); i++) {
                JSONObject movieJSON = movieArray.getJSONObject(i);
                resultStrs[i] = movieJSON.getString(MOVIEDB_POSTER);
                Log.v(LOG_TAG, resultStrs[i]);
            }
            return resultStrs;
        }
        @Override
        protected void onPostExecute(String[] strings) {
            if (strings != null) {
                List<String> posters = new ArrayList<>(Arrays.asList(strings));

                mPosterLocations.clear();
                mPosterLocations.addAll(posters);
                imageAdapter.notifyDataSetChanged();
            }
            super.onPostExecute(strings);
        }

    }
}