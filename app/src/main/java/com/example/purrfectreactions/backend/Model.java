package com.example.purrfectreactions.backend;

import android.content.ContentValues;
import android.content.Context;

import com.google.common.collect.Sets;

import com.google.mediapipe.tasks.components.containers.Embedding;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder;
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions;
import com.google.mediapipe.tasks.text.textembedder.TextEmbedderResult;


import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Optional;
import java.util.ArrayList;

public class Model {

    private static final String MODEL_FILE = "universal_sentence_encoder.tflite";

    private dbHelper db;
    private TextEmbedder textEmbedder;

    // Opens the database. On first launch this creates the table and fills it from the CSV.
    // Call once from MainActivity.onCreate():
    //     model.initDatabase(this);
    public void initDatabase(Context context) {
        if (db != null) return; // already open

        initEmbedder(context); // seeding needs the embedder, so make sure it's loaded first
        db = new dbHelper(context.getApplicationContext(), this);
        db.getWritableDatabase(); // triggers dbHelper.onCreate() only if the DB doesn't exist yet
    }

    // Loads the embedding model from assets
    public void initEmbedder(Context context) {
        if (textEmbedder != null) return; // already loaded

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .build();

        TextEmbedderOptions options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build();

        textEmbedder = TextEmbedder.createFromOptions(context.getApplicationContext(), options);
    }

    // Helper method: Embeds String text
    public Embedding embed(String txt) {
        if (textEmbedder == null) {
            throw new IllegalStateException("Embedder not loaded. Call initEmbedder() first.");
        }
        TextEmbedderResult result = textEmbedder.embed(txt);
        return result.embeddingResult().embeddings().get(0);
    }

    // Helper method: Computes Cosine Similarity between two Embeddings
    public double similarity(Embedding a, Embedding b) {
        return TextEmbedder.cosineSimilarity(a, b);
    }

    // Helper method: Converts an Embedding to a string like "[0.0123,-0.0456,...]" for storage
    public String embeddingToString(Embedding embedding) {
        float[] vector = embedding.floatEmbedding();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    // Helper method: Converts a stored string like "[0.0123,-0.0456,...]" back into an Embedding
    public Embedding stringToEmbedding(String str) {
        String trimmed = str.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] parts = trimmed.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }

        // float vector, no quantized vector, head index 0, no head name
        return Embedding.create(vector, new byte[0], 0, Optional.empty());
    }

    // Helper method: Converts a tags JSON string like ["Cooked", "Unbothered"] into a list
    private ArrayList<String> parseTags(String json) {
        ArrayList<String> tags = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                tags.add(arr.getString(i));
            }
        } catch (JSONException e) {
            // malformed tags; return whatever was parsed (likely empty)
        }
        return tags;
    }

    // Helper method: true if imageTags contains every tag in wanted (case-insensitive)
    private boolean hasAllTags(ArrayList<String> imageTags, String[] wanted) {
        if (wanted == null || wanted.length == 0) return true;

        for (String w : wanted) {
            boolean found = false;
            for (String t : imageTags) {
                if (t.equalsIgnoreCase(w.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    // Holds the info for one cat image, returned by the search functions
    public static class ImageResource {
        private final int imageID;
        private final ArrayList<String> tags;
        private boolean isFavorite;
        private double similarity; // only meaningful for messageContextSearch results

        public ImageResource(int imageID, ArrayList<String> tags, boolean isFavorite) {
            this.imageID = imageID;
            this.tags = tags;
            this.isFavorite = isFavorite;
            this.similarity = 0.0;
        }

        public int getImageID() {
            return imageID;
        }

        public ArrayList<String> getTags() {
            return tags;
        }

        public boolean isFavorite() {
            return isFavorite;
        }

        public void setFavorite(boolean favorite) {
            isFavorite = favorite;
        }

        public double getSimilarity() {
            return similarity;
        }

        public void setSimilarity(double similarity) {
            this.similarity = similarity;
        }

        // Path to the image inside assets, e.g. imageID 1 -> "01.png"
        public String getAssetPath() {
            return String.format(java.util.Locale.US, "%02d.png", imageID);
        }
    }

    // Sets img to favorite or unfavorite
    public void setFavorite(int imgID, boolean fav) {
        if (db == null) {
            throw new IllegalStateException("Database not initialized. Call initDatabase() first.");
        }

        ContentValues values = new ContentValues();
        values.put(dbHelper.COL_IS_FAVORITE, fav ? 1 : 0);

        db.getWritableDatabase().update(
                dbHelper.TABLE_IMAGES,
                values,
                dbHelper.COL_IMAGE_ID + " = ?",
                new String[]{ String.valueOf(imgID) }
        );
    }

    // Similarity threshold settings for messageContextSearch
    private static final double START_THRESHOLD = 0.50;
    private static final double THRESHOLD_STEP = 0.05;

    // Embeds context, Computes similarity with images, and return list sorted by similarity
    // Only returns images at or above the similarity threshold. If none qualify,
    // the threshold is lowered step by step until at least one image does.
    public ArrayList<ImageResource> messageContextSearch(String context) {
        if (db == null) {
            throw new IllegalStateException("Database not initialized. Call initDatabase() first.");
        }

        // 1. Embed the user's message
        Embedding messageEmbedding = embed(context);

        ArrayList<ImageResource> scored = new ArrayList<>();

        // 2. Read every image from the database and score it
        try (Cursor cursor = db.getReadableDatabase().query(
                dbHelper.TABLE_IMAGES,
                new String[]{ dbHelper.COL_IMAGE_ID, dbHelper.COL_IS_FAVORITE,
                        dbHelper.COL_TAGS, dbHelper.COL_EMBEDDED },
                null, null, null, null, null)) {

            int idCol = cursor.getColumnIndexOrThrow(dbHelper.COL_IMAGE_ID);
            int favCol = cursor.getColumnIndexOrThrow(dbHelper.COL_IS_FAVORITE);
            int tagsCol = cursor.getColumnIndexOrThrow(dbHelper.COL_TAGS);
            int embCol = cursor.getColumnIndexOrThrow(dbHelper.COL_EMBEDDED);

            while (cursor.moveToNext()) {
                int imageID = cursor.getInt(idCol);
                boolean isFavorite = cursor.getInt(favCol) == 1;
                ArrayList<String> tags = parseTags(cursor.getString(tagsCol));

                Embedding imageEmbedding = stringToEmbedding(cursor.getString(embCol));
                double score = similarity(messageEmbedding, imageEmbedding);

                ImageResource img = new ImageResource(imageID, tags, isFavorite);
                img.setSimilarity(score);
                scored.add(img);
            }
        }

        if (scored.isEmpty()) return scored; // empty database

        // 3. Sort highest similarity first
        scored.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // 4. Keep images above the threshold; lower it until at least one qualifies
        double threshold = START_THRESHOLD;
        ArrayList<ImageResource> results = new ArrayList<>();
        while (results.isEmpty()) {
            for (ImageResource img : scored) {
                if (img.getSimilarity() >= threshold) {
                    results.add(img);
                } else {
                    break; // list is sorted, so everything after this is lower
                }
            }
            threshold -= THRESHOLD_STEP;
        }

        return results;
    }

    // Filters by tag and returns ArrayList of ImageResources
    // An image is included only if it has ALL of the given tags (case-insensitive).
    // If tags is null or empty, every image is returned.
    public ArrayList<ImageResource> tagFilteredSearch(String[] tags) {
        if (db == null) {
            throw new IllegalStateException("Database not initialized. Call initDatabase() first.");
        }

        ArrayList<ImageResource> results = new ArrayList<>();

        try (Cursor cursor = db.getReadableDatabase().query(
                dbHelper.TABLE_IMAGES,
                new String[]{ dbHelper.COL_IMAGE_ID, dbHelper.COL_IS_FAVORITE, dbHelper.COL_TAGS },
                null, null, null, null,
                dbHelper.COL_IMAGE_ID + " ASC")) {

            int idCol = cursor.getColumnIndexOrThrow(dbHelper.COL_IMAGE_ID);
            int favCol = cursor.getColumnIndexOrThrow(dbHelper.COL_IS_FAVORITE);
            int tagsCol = cursor.getColumnIndexOrThrow(dbHelper.COL_TAGS);

            while (cursor.moveToNext()) {
                ArrayList<String> imageTags = parseTags(cursor.getString(tagsCol));

                if (hasAllTags(imageTags, tags)) {
                    int imageID = cursor.getInt(idCol);
                    boolean isFavorite = cursor.getInt(favCol) == 1;
                    results.add(new ImageResource(imageID, imageTags, isFavorite));
                }
            }
        }

        return results;
    }

    // Returns list of favorited images
    public ArrayList<ImageResource> favoritedImagesSearch() {
        if (db == null) {
            throw new IllegalStateException("Database not initialized. Call initDatabase() first.");
        }

        ArrayList<ImageResource> results = new ArrayList<>();

        try (Cursor cursor = db.getReadableDatabase().query(
                dbHelper.TABLE_IMAGES,
                new String[]{ dbHelper.COL_IMAGE_ID, dbHelper.COL_TAGS },
                dbHelper.COL_IS_FAVORITE + " = ?",
                new String[]{ "1" },
                null, null,
                dbHelper.COL_IMAGE_ID + " ASC")) {

            int idCol = cursor.getColumnIndexOrThrow(dbHelper.COL_IMAGE_ID);
            int tagsCol = cursor.getColumnIndexOrThrow(dbHelper.COL_TAGS);

            while (cursor.moveToNext()) {
                int imageID = cursor.getInt(idCol);
                ArrayList<String> tags = parseTags(cursor.getString(tagsCol));
                results.add(new ImageResource(imageID, tags, true));
            }
        }

        return results;
    }

    // Returns ImageResource from a random cat search
    public ImageResource randomCatImageSearch() {
        if (db == null) {
            throw new IllegalStateException("Database not initialized. Call initDatabase() first.");
        }

        try (Cursor cursor = db.getReadableDatabase().query(
                dbHelper.TABLE_IMAGES,
                new String[]{ dbHelper.COL_IMAGE_ID, dbHelper.COL_IS_FAVORITE, dbHelper.COL_TAGS },
                null, null, null, null,
                "RANDOM()",   // shuffle the rows
                "1")) {       // take just one

            if (cursor.moveToFirst()) {
                int imageID = cursor.getInt(cursor.getColumnIndexOrThrow(dbHelper.COL_IMAGE_ID));
                boolean isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(dbHelper.COL_IS_FAVORITE)) == 1;
                ArrayList<String> tags = parseTags(cursor.getString(cursor.getColumnIndexOrThrow(dbHelper.COL_TAGS)));
                return new ImageResource(imageID, tags, isFavorite);
            }
        }

        return null; // only if the database is empty
    }

}