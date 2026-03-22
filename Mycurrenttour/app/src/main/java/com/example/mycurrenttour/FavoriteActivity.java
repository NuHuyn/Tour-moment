package com.example.mycurrenttour;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class FavoriteActivity extends AppCompatActivity {

    RecyclerView recyclerFavorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        recyclerFavorite = findViewById(R.id.recyclerFavorite);

        ArrayList<Tour> list = new ArrayList<>();

        // Tour 1
        Tour t1 = new Tour();
        t1.setTour_name("Du thuyền Sài Gòn với bữa tối");
        t1.setPrice(80);
        t1.setImage_url("https://images.unsplash.com/photo-1758784549597-1747bd58c192?w=1000&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8MjR8fHRyYWRpdGlvbmFsJTIwdmlsbGFnZSUyMGluJTIwdmlldG5hbXxlbnwwfHwwfHx8MA%3D%3D");

        // Tour 2
        Tour t2 = new Tour();
        t2.setTour_name("Trải nghiệm thuyền Nhiêu Lộc");
        t2.setPrice(280);
        t2.setImage_url("https://images.unsplash.com/photo-1758784549597-1747bd58c192?w=1000&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8MjR8fHRyYWRpdGlvbmFsJTIwdmlsbGFnZSUyMGluJTIwdmlldG5hbXxlbnwwfHwwfHx8MA%3D%3D");

        // Tour 3
        Tour t3 = new Tour();
        t3.setTour_name("TP HCM: Go Kart & Bắn súng sơn");
        t3.setPrice(320);
        t3.setImage_url("https://images.unsplash.com/photo-1758784549597-1747bd58c192?w=1000&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8MjR8fHRyYWRpdGlvbmFsJTIwdmlsbGFnZSUyMGluJTIwdmlldG5hbXxlbnwwfHwwfHx8MA%3D%3D");

        list.add(t1);
        list.add(t2);
        list.add(t3);

        FavoriteAdapter adapter = new FavoriteAdapter(list);

        recyclerFavorite.setLayoutManager(new LinearLayoutManager(this));
        recyclerFavorite.setAdapter(adapter);
    }
}