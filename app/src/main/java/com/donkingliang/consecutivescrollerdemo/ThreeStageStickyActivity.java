package com.donkingliang.consecutivescrollerdemo;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.donkingliang.consecutivescroller.ConsecutiveScrollerLayout;
import com.donkingliang.consecutivescrollerdemo.adapter.RecyclerViewAdapter;

public class ThreeStageStickyActivity extends AppCompatActivity {

    private ConsecutiveScrollerLayout scrollerLayout;
    private View headerView;
    private RecyclerView recyclerView;

    // Heights in pixels
    private int initialHeight;
    private int anchorHeight;
    private int minHeight;

    private int scrollOffsetToAnchor; // 300 - 100 = 200
    private int scrollOffsetToMin;    // 300 - 50 = 250

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_three_stage_sticky);

        scrollerLayout = findViewById(R.id.scrollerLayout);
        headerView = findViewById(R.id.headerView);
        recyclerView = findViewById(R.id.recyclerView);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new RecyclerViewAdapter(this, "Three Stage Sticky"));

        // Initialize dimensions
        // Note: In a real app, convert dp to px. 
        // Here we assume standard density or fetch from resources if we had defined dimens.
        // Hardcoding based on layout: 300dp, 100dp, 50dp.
        float density = getResources().getDisplayMetrics().density;
        initialHeight = (int) (300 * density);
        anchorHeight = (int) (100 * density);
        minHeight = (int) (50 * density);

        scrollOffsetToAnchor = initialHeight - anchorHeight;
        scrollOffsetToMin = initialHeight - minHeight;

        // Ensure Header is drawn on top of RecyclerView when they overlap
        headerView.setElevation(10 * density);

        // Set snap points
        scrollerLayout.addSnapPoint(0);
        scrollerLayout.addSnapPoint(scrollOffsetToAnchor);
        // Note: We don't necessarily need to snap to scrollOffsetToMin if it's the sticky point,
        // but adding it makes the behavior consistent.
        scrollerLayout.addSnapPoint(scrollOffsetToMin);
    }
}
