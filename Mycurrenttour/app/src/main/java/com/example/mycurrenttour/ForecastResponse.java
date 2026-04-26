package com.example.mycurrenttour;

import java.util.List;

public class ForecastResponse {

    public List<Item> list;

    public class Item {
        public String dt_txt;
        public Main main;
    }

    public class Main {
        public float temp;
    }
}