package com.example.purrfectreactions.backend;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class dbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "catDB.db";
    public static final int DB_VERSION = 1;
    public static final String TABLE_IMAGES = "imageTable";
    public static final String CSV_FILE = "cat_images.csv";

    // Column names
    public static final String COL_IMAGE_ID = "imageID";
    public static final String COL_IS_FAVORITE = "isFavorite";
    public static final String COL_TAGS = "tags";
    public static final String COL_EMBEDDED = "embedded";

    private final Context context;
    private final Model model;

    public dbHelper(Context context, Model model) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        this.model = model;
    }

    // Only runs when the database doesn't exist yet (first launch)
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_IMAGES + " (" +
                        COL_IMAGE_ID + " INTEGER PRIMARY KEY, " +
                        COL_IS_FAVORITE + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_TAGS + " TEXT NOT NULL, " +
                        COL_EMBEDDED + " TEXT" +
                        ");"
        );
        seedFromCsv(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion,
                          int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IMAGES);
        onCreate(db);
    }

    // Reads cat_images.csv from assets, embeds each image's text, and inserts every row
    private void seedFromCsv(SQLiteDatabase db) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                context.getAssets().open(CSV_FILE), StandardCharsets.UTF_8))) {

            br.readLine(); // skip header: id,name,tags,embedding_text
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> fields = parseCsvLine(line);
                int imageID = Integer.parseInt(fields.get(0).trim()); // "01" -> 1
                String tags = fields.get(2);                          // ["Cooked", "Unbothered"]
                String embeddingText = fields.get(3);

                String embedded = model.embeddingToString(model.embed(embeddingText));

                ContentValues values = new ContentValues();
                values.put(COL_IMAGE_ID, imageID);
                values.put(COL_IS_FAVORITE, 0);
                values.put(COL_TAGS, tags);
                values.put(COL_EMBEDDED, embedded);
                db.insertOrThrow(TABLE_IMAGES, null, values);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to seed database from " + CSV_FILE, e);
        }
    }

    // Splits one CSV line into fields, respecting quoted fields that contain commas
    // and "" (escaped quotes) inside them
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"'); // escaped quote
                        i++;
                    } else {
                        inQuotes = false;    // end of quoted field
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }
}