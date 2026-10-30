package com.example.purrfectreactions.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.purrfectreactions.R;
import com.google.android.material.tabs.TabLayout;

public class SearchFragment extends Fragment {
    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TabLayout tabs = view.findViewById(R.id.searchTabs);
        tabs.addTab(tabs.newTab().setText("Text"));
        tabs.addTab(tabs.newTab().setText("Tag"));

        if (savedInstanceState == null) {
            showChild(new TextSearchFragment());
        }

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    showChild(new TextSearchFragment());
                } else {
                    showChild(new TagSearchFragment());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showChild(Fragment fragment) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.innerSLayout, fragment)
                .commit();
    }
}