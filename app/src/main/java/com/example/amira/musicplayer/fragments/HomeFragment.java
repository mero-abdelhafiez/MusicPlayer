package com.example.amira.musicplayer.fragments;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.amira.musicplayer.R;
import com.example.amira.musicplayer.adapters.AutoCompleteAdapter;
import com.example.amira.musicplayer.adapters.RecentAdapter;
import com.example.amira.musicplayer.models.Album;
import com.example.amira.musicplayer.ui.ResultActivity;
import com.example.amira.musicplayer.utils.JsonUtils;
import com.example.amira.musicplayer.utils.LayoutUtils;
import com.example.amira.musicplayer.utils.NetworkUtils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Amira on 1/27/2019.
 */

public class HomeFragment extends Fragment implements RecentAdapter.ItemOnClickHandler {

    private static final String LOG_TAG = HomeFragment.class.getSimpleName();
    private Context mContext;
    private Album[] mNewReleases;
    private RecyclerView mRecentRecyclerView;
    private RecentAdapter mRecentAdapter;
    private GridLayoutManager mLayoutManager;
    private SearchView mSearchView;
    private SharedPreferences prefs;
    private AutoCompleteAdapter mSearchAdapter;
    private ListView mSearchSuggestionList;
    private List<String> mSuggestionsList;

    private ProgressBar mLoadingBar;
    private TextView mErrorMessage;
    private static final String RV_POSITION = "rvposition";
    private int mCurrentScrollPosition = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getActivity().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        mContext = getContext();
        prefs = mContext.getSharedPreferences("MusicToken", MODE_PRIVATE);
        View rootView = inflater.inflate(R.layout.fragment_home , container , false);

        mRecentRecyclerView = rootView.findViewById(R.id.rv_new_releases);
        mLoadingBar = rootView.findViewById(R.id.pb_loading_bar);
        mErrorMessage = rootView.findViewById(R.id.tv_error_message);
        mSearchSuggestionList = (ListView) rootView.findViewById(R.id.search_suggestions_list);
        mSearchView = rootView.findViewById(R.id.search_text);

        mSearchAdapter = new AutoCompleteAdapter(mContext);
        List<String> suggestions = new ArrayList<>();
        mSearchAdapter.setmSearchSuggestions(suggestions);
        mSearchSuggestionList.setAdapter(mSearchAdapter);
        mSearchSuggestionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openSearchResult(mSuggestionsList.get(position));
            }
        });

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                openSearchResult(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(newText.isEmpty()){
                    List<String> suggestions = new ArrayList<>();
                    mSearchAdapter.setmSearchSuggestions(suggestions);
                }
                new SearchQuery().execute(newText);
                return false;
            }
        });
        mRecentAdapter = new RecentAdapter(mContext, this);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int value = displayMetrics.widthPixels;
        int valueDp = (int) LayoutUtils.convertPxToDp(mContext, (float)value);

        boolean IsLargeScreen = (valueDp > 600);
        int span , scalingFactor;
        if(IsLargeScreen){
            scalingFactor = 200;
        }else {
            scalingFactor = 150;
        }
        span = LayoutUtils.calculateNoOfColumns(mContext , scalingFactor);

        mLayoutManager = new GridLayoutManager(mContext , span);
        mRecentRecyclerView.setAdapter(mRecentAdapter);
        mRecentRecyclerView.setLayoutManager(mLayoutManager);
        hideErrorMessage();
        new RecentQuery().execute();
        populateData();
        return rootView;
    }

    private void populateData() {
        if(mNewReleases != null){
            mRecentAdapter.setmAlbums(mNewReleases);
            setUserVisibleHint(true);
        }
    }

    public void setmContext(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (getFragmentManager() != null) {

            getFragmentManager()
                    .beginTransaction()
                    .detach(this)
                    .attach(this)
                    .commit();
        }
    }

    @Override
    public void onClickItem(int position) {
        if(mNewReleases == null) return;
        String url = NetworkUtils.WEBSITE_URL + mNewReleases[position].getID();
        if(url != null) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    class RecentQuery extends AsyncTask<Void , Void , String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showLoadingSpinner();
        }

        @Override
        protected String doInBackground(Void... voids) {
            String result = null;
            URL url = NetworkUtils.buildRecentDataUrl();
            Log.d("ParsedJson" , url.toString());
            String token = prefs.getString("token", null);

            try {
                result = NetworkUtils.getData(url , token);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s != null) {
                Log.d("ParsedJson" , s);
                mNewReleases = JsonUtils.ParseNewReleasesAlbums(s);
                mRecentAdapter.setmAlbums(mNewReleases);
                if(prefs.contains(RV_POSITION)){
                    mCurrentScrollPosition = prefs.getInt(RV_POSITION , 0);
                }
                mLayoutManager.scrollToPosition(mCurrentScrollPosition);
                Log.d("ParsedJson" , "Here" + mNewReleases[0].getName());
                Log.d("ParsedJson" , "Here" + mNewReleases[0].getImage());
                hideLoadingSpinner();
                hideErrorMessage();
            }else{
                //Toast.makeText(mContext , "Null Data" , Toast.LENGTH_SHORT).show();
                hideLoadingSpinner();
                showErrorMessage();
            }
        }
    }


    class SearchQuery extends AsyncTask<String , Void , String>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... strings) {
            String result = null;
            String phrase = strings[0];
            URL url = NetworkUtils.buildSearchDataUrl(phrase , "5");
            Log.d("ParsedJson" , url.toString());
            String token = prefs.getString("token" , null);
            try {
                result = NetworkUtils.getData(url , token);
            } catch (IOException e) {
                Log.d("ParsedJson" , "Exception" + e.getMessage());
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s != null){
                List<String> names = JsonUtils.ParseSearchAlbumsSearch(s);
                //Log.d("ParsedJson" , Integer.toString(mAlbums.length));
                mSuggestionsList = names;
                mSearchAdapter.setmSearchSuggestions(names);
            }else{
            }
        }
    }

    private void openSearchResult(String queryStr){
        Intent intent = new Intent(mContext , ResultActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT , queryStr);
        startActivity(intent);
    }

    private void showErrorMessage(){
        mRecentRecyclerView.setVisibility(View.INVISIBLE);
        mErrorMessage.setVisibility(View.VISIBLE);
    }
    private void hideErrorMessage(){
        mErrorMessage.setVisibility(View.INVISIBLE);
        mRecentRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showLoadingSpinner(){
        mLoadingBar.setVisibility(View.VISIBLE);
        mRecentRecyclerView.setVisibility(View.INVISIBLE);
    }

    private void hideLoadingSpinner(){
        mLoadingBar.setVisibility(View.INVISIBLE);
        mRecentRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        int currentVisiblePosition = 0;
        currentVisiblePosition = mLayoutManager.findFirstVisibleItemPosition();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(RV_POSITION , currentVisiblePosition);
        editor.apply();
        super.onSaveInstanceState(outState);
    }
}
