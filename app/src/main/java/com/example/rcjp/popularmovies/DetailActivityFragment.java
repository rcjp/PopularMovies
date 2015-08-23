package com.example.rcjp.popularmovies;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailActivityFragment extends Fragment {

    public DetailActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View detailView = inflater.inflate(R.layout.fragment_detail, container, false);
        Intent intent = getActivity().getIntent();
        Bundle bundle = intent.getExtras();

        if (intent != null && intent.hasExtra("title")) {
            double rating = bundle.getDouble("rating");
            String title = bundle.getString("title") + String.format(" (%.1f/10)", rating);
            ((TextView) detailView.findViewById(R.id.movie_title)).setText(title);
            ((TextView) detailView.findViewById(R.id.movie_date)).setText(bundle.getString("date"));
            ((TextView) detailView.findViewById(R.id.movie_synopsis)).setText(bundle.getString("synopsis"));

            String imageURLStr = bundle.getString("imageURL");

            // Use Picasso to cache load the images for movies
            try {
                ImageView imageView = ((ImageView) detailView.findViewById(R.id.movie_poster));
                Picasso.with(detailView.getContext()).load(imageURLStr).into(imageView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return detailView;
    }
}
