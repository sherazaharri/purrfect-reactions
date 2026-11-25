package com.example.purrfectreactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.purrfectreactions.backend.Model;
import com.example.purrfectreactions.backend.dbHelper;
import com.google.mediapipe.tasks.components.containers.Embedding;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Instrumented tests for Model + dbHelper (Purrfect Reactions).
 *
 * Location: app/src/androidTest/java/com/example/purrfectreactions/ModelTest.java
 * Run:      right-click the class (or a single test) -> Run, with an emulator/device running.
 *
 * Expected values below were computed from cat_images.csv (90 images, 26 tags).
 *
 * NOTE: these tests use the app's real database. Every test resets all favorites to
 * false before and after it runs, so any favorites on the test device will be cleared.
 */
@RunWith(AndroidJUnit4.class)
public class ModelTest {

    private static Model model;
    private static Context context;

    // ---------------------------------------------------------------------------------
    // Expected results: every tag -> the exact image IDs that have it (from the CSV)
    // ---------------------------------------------------------------------------------
    private static final Map<String, List<Integer>> EXPECTED_TAGS = new LinkedHashMap<>();
    static {
        EXPECTED_TAGS.put("Happy",        Arrays.asList(10, 11, 38, 68, 77, 80, 81, 88, 89));
        EXPECTED_TAGS.put("Sad",          Arrays.asList(2, 5, 14, 26, 37, 44, 54));
        EXPECTED_TAGS.put("Angry",        Arrays.asList(6, 7, 16, 42, 43, 49, 61));
        EXPECTED_TAGS.put("Shocked",      Arrays.asList(14, 15, 20, 30, 31, 62, 69, 78, 79, 84));
        EXPECTED_TAGS.put("Confused",     Arrays.asList(9, 17, 20, 24, 33, 47, 63, 83));
        EXPECTED_TAGS.put("Thinking",     Arrays.asList(33, 54, 60, 71, 72));
        EXPECTED_TAGS.put("Tired",        Arrays.asList(4, 7, 32, 60, 75, 86, 90));
        EXPECTED_TAGS.put("Food",         Arrays.asList(2, 19, 34, 50, 52, 82, 85, 88));
        EXPECTED_TAGS.put("Hello/Bye",    Arrays.asList(4, 26, 29, 44, 48, 53, 56, 87));
        EXPECTED_TAGS.put("Love",         Arrays.asList(23, 38, 56, 76, 81, 87));
        EXPECTED_TAGS.put("Scary",        Arrays.asList(7, 36, 43, 62, 64, 79));
        EXPECTED_TAGS.put("Chonky",       Arrays.asList(13, 21, 22, 23, 66, 82));
        EXPECTED_TAGS.put("Gross/TMI",    Arrays.asList(8, 19, 27, 50, 77));
        EXPECTED_TAGS.put("Cooked",       Arrays.asList(1, 6, 14, 26, 30, 36, 37, 79));
        EXPECTED_TAGS.put("Crashout",     Arrays.asList(6, 25, 36, 37, 42, 43, 61));
        EXPECTED_TAGS.put("Side-Eye",     Arrays.asList(9, 12, 33, 39, 40, 41, 64, 67, 83));
        EXPECTED_TAGS.put("Unbothered",   Arrays.asList(1, 13, 32, 35, 60, 66, 68, 90));
        EXPECTED_TAGS.put("Locked In",    Arrays.asList(3, 45, 58, 59, 65, 78, 80));
        EXPECTED_TAGS.put("Aura",         Arrays.asList(18, 29, 41, 52, 65, 68, 70));
        EXPECTED_TAGS.put("Delulu",       Arrays.asList(17, 28, 46, 51, 55, 70, 85));
        EXPECTED_TAGS.put("Brainrot",     Arrays.asList(10, 11, 15, 19, 24, 28, 46, 47, 48, 57, 63, 82, 88));
        EXPECTED_TAGS.put("Rizz",         Arrays.asList(4, 29, 53, 56, 76, 87));
        EXPECTED_TAGS.put("Caught in 4K", Arrays.asList(3, 57, 58, 67, 83, 84));
        EXPECTED_TAGS.put("Hell Nah",     Arrays.asList(16, 20, 31, 44, 49, 73, 86));
        EXPECTED_TAGS.put("W",            Arrays.asList(18, 38, 52, 67, 74, 89));
        EXPECTED_TAGS.put("L",            Arrays.asList(5, 14, 22, 26, 37, 73));
    }

    private static final int TOTAL_IMAGES = 90;
    private static final int TOTAL_TAG_ASSIGNMENTS = 189; // sum of all images' tag counts
    private static final double EPS = 1e-6;

    // ---------------------------------------------------------------------------------
    // Setup / teardown
    // ---------------------------------------------------------------------------------
    @BeforeClass
    public static void setUpClass() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        model = new Model();
        model.initDatabase(context); // loads embedder + seeds DB if it doesn't exist yet
    }

    @Before
    public void setUp() {
        resetFavorites();
    }

    @After
    public void tearDown() {
        resetFavorites();
    }

    private static void resetFavorites() {
        for (int id = 1; id <= TOTAL_IMAGES; id++) {
            model.setFavorite(id, false);
        }
    }

    private static List<Integer> ids(List<Model.ImageResource> images) {
        List<Integer> out = new ArrayList<>();
        for (Model.ImageResource img : images) out.add(img.getImageID());
        return out;
    }

    private static List<Integer> range(int from, int toInclusive) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i <= toInclusive; i++) out.add(i);
        return out;
    }

    // =================================================================================
    // 1. DATABASE SEEDING
    // =================================================================================

    // Expected: exactly 90 rows, IDs 1..90, every row has tags and a non-empty embedding,
    // and all embeddings have the same length.
    @Test
    public void db_hasAll90RowsWithTagsAndEmbeddings() {
        dbHelper helper = new dbHelper(context, model);
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + dbHelper.TABLE_IMAGES, null)) {
            c.moveToFirst();
            assertEquals(TOTAL_IMAGES, c.getInt(0));
        }

        try (Cursor c = db.rawQuery("SELECT MIN(" + dbHelper.COL_IMAGE_ID + "), MAX("
                + dbHelper.COL_IMAGE_ID + ") FROM " + dbHelper.TABLE_IMAGES, null)) {
            c.moveToFirst();
            assertEquals(1, c.getInt(0));
            assertEquals(TOTAL_IMAGES, c.getInt(1));
        }

        int expectedDims = -1;
        try (Cursor c = db.query(dbHelper.TABLE_IMAGES,
                new String[]{dbHelper.COL_IMAGE_ID, dbHelper.COL_TAGS, dbHelper.COL_EMBEDDED},
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String tags = c.getString(1);
                String embedded = c.getString(2);

                assertNotNull("tags null for id " + id, tags);
                assertTrue("tags not a JSON array for id " + id,
                        tags.startsWith("[") && tags.endsWith("]"));
                assertFalse("tags should not contain CSV-escaped quotes for id " + id,
                        tags.contains("\"\""));

                assertNotNull("embedded null for id " + id, embedded);
                int dims = model.stringToEmbedding(embedded).floatEmbedding().length;
                assertTrue("empty embedding for id " + id, dims > 0);
                if (expectedDims == -1) expectedDims = dims;
                assertEquals("embedding length differs for id " + id, expectedDims, dims);
            }
        }
        db.close();
    }

    // Expected: favorites all start at 0 after seeding (reset here, then checked).
    @Test
    public void db_noFavoritesAfterReset() {
        assertTrue(model.favoritedImagesSearch().isEmpty());
    }

    // Expected: total of all tag lists = 189, and every image has at least 1 tag.
    @Test
    public void db_tagTotalsMatchCsv() {
        List<Model.ImageResource> all = model.tagFilteredSearch(new String[]{});
        int total = 0;
        for (Model.ImageResource img : all) {
            assertFalse("image " + img.getImageID() + " has no tags", img.getTags().isEmpty());
            total += img.getTags().size();
        }
        assertEquals(TOTAL_TAG_ASSIGNMENTS, total);
    }

    // Expected: specific rows parsed exactly as in the CSV (order preserved).
    @Test
    public void db_specificRowsHaveExactTags() {
        Map<Integer, List<String>> expected = new LinkedHashMap<>();
        expected.put(1,  Arrays.asList("Cooked", "Unbothered"));          // AloofKitty
        expected.put(14, Arrays.asList("Sad", "Shocked", "Cooked", "L")); // Cooked
        expected.put(27, Arrays.asList("Gross/TMI"));                     // GottaShit
        expected.put(56, Arrays.asList("Hello/Bye", "Love", "Rizz"));     // PlsPickup
        expected.put(75, Arrays.asList("Tired"));                         // TooEarlyForThis
        expected.put(90, Arrays.asList("Tired", "Unbothered"));           // Zaza

        Map<Integer, List<String>> actual = new LinkedHashMap<>();
        for (Model.ImageResource img : model.tagFilteredSearch(null)) {
            actual.put(img.getImageID(), img.getTags());
        }
        for (Map.Entry<Integer, List<String>> e : expected.entrySet()) {
            assertEquals("tags for image " + e.getKey(), e.getValue(), actual.get(e.getKey()));
        }
    }

    // =================================================================================
    // 2. IMAGE RESOURCE
    // =================================================================================

    // Expected: 1 -> "01.png", 9 -> "09.png", 10 -> "10.png", 90 -> "90.png"
    @Test
    public void imageResource_assetPathIsZeroPadded() {
        assertEquals("01.png", new Model.ImageResource(1, new ArrayList<>(), false).getAssetPath());
        assertEquals("09.png", new Model.ImageResource(9, new ArrayList<>(), false).getAssetPath());
        assertEquals("10.png", new Model.ImageResource(10, new ArrayList<>(), false).getAssetPath());
        assertEquals("90.png", new Model.ImageResource(90, new ArrayList<>(), false).getAssetPath());
    }

    // Expected: every image's asset path opens a real file in assets/
    @Test
    public void imageResource_everyAssetFileExists() throws Exception {
        for (Model.ImageResource img : model.tagFilteredSearch(null)) {
            try (InputStream in = context.getAssets().open(img.getAssetPath())) {
                assertTrue("empty file: " + img.getAssetPath(), in.read() != -1);
            }
        }
    }

    // Expected: getters/setters return what was set; similarity defaults to 0.0
    @Test
    public void imageResource_gettersAndSetters() {
        ArrayList<String> tags = new ArrayList<>(Arrays.asList("Cooked", "Sad"));
        Model.ImageResource img = new Model.ImageResource(14, tags, false);

        assertEquals(14, img.getImageID());
        assertEquals(tags, img.getTags());
        assertFalse(img.isFavorite());
        assertEquals(0.0, img.getSimilarity(), EPS);

        img.setFavorite(true);
        img.setSimilarity(0.73);
        assertTrue(img.isFavorite());
        assertEquals(0.73, img.getSimilarity(), EPS);
    }

    // =================================================================================
    // 3. TAG FILTERED SEARCH
    // =================================================================================

    // Expected: each of the 26 tags returns exactly the IDs in EXPECTED_TAGS, in ID order.
    @Test
    public void tagSearch_everySingleTagMatchesCsv() {
        for (Map.Entry<String, List<Integer>> e : EXPECTED_TAGS.entrySet()) {
            List<Integer> actual = ids(model.tagFilteredSearch(new String[]{e.getKey()}));
            assertEquals("tag: " + e.getKey(), e.getValue(), actual);
        }
    }

    // Expected: empty array and null both return all 90 images, IDs 1..90 in order.
    @Test
    public void tagSearch_noTagsReturnsAll() {
        assertEquals(range(1, 90), ids(model.tagFilteredSearch(new String[]{})));
        assertEquals(range(1, 90), ids(model.tagFilteredSearch(null)));
    }

    // Expected: matching ignores case and surrounding spaces.
    @Test
    public void tagSearch_caseAndWhitespaceInsensitive() {
        List<Integer> cooked = EXPECTED_TAGS.get("Cooked");
        assertEquals(cooked, ids(model.tagFilteredSearch(new String[]{"cooked"})));
        assertEquals(cooked, ids(model.tagFilteredSearch(new String[]{"COOKED"})));
        assertEquals(cooked, ids(model.tagFilteredSearch(new String[]{"  Cooked  "})));
        assertEquals(EXPECTED_TAGS.get("Locked In"),
                ids(model.tagFilteredSearch(new String[]{"locked in"})));
    }

    // Expected: multiple tags = AND (image must have all of them).
    @Test
    public void tagSearch_multipleTagsAreAnd() {
        assertEquals(Arrays.asList(14, 26, 37),
                ids(model.tagFilteredSearch(new String[]{"Cooked", "Sad"})));
        assertEquals(Arrays.asList(6, 36, 37),
                ids(model.tagFilteredSearch(new String[]{"Cooked", "Crashout"})));
        assertEquals(Arrays.asList(38, 89),
                ids(model.tagFilteredSearch(new String[]{"Happy", "W"})));
        assertEquals(Arrays.asList(9, 33, 83),
                ids(model.tagFilteredSearch(new String[]{"Side-Eye", "Confused"})));
        assertEquals(Arrays.asList(14, 26, 37),
                ids(model.tagFilteredSearch(new String[]{"Sad", "Cooked", "L"})));
    }

    // Expected: contradictory or nonexistent tags return an empty list (not null).
    @Test
    public void tagSearch_noMatchesReturnsEmptyList() {
        List<Model.ImageResource> none = model.tagFilteredSearch(new String[]{"Happy", "Angry"});
        assertNotNull(none);
        assertTrue(none.isEmpty());
        assertTrue(model.tagFilteredSearch(new String[]{"NotARealTag"}).isEmpty());
    }

    // Expected: partial tag names do NOT match ("Cook" is not "Cooked", "Lock" is not "Locked In").
    @Test
    public void tagSearch_partialNamesDoNotMatch() {
        assertTrue(model.tagFilteredSearch(new String[]{"Cook"}).isEmpty());
        assertTrue(model.tagFilteredSearch(new String[]{"Lock"}).isEmpty());
    }

    // =================================================================================
    // 4. FAVORITES
    // =================================================================================

    // Expected: favoriting 74, 5, 37 -> favorites list is [5, 37, 74] (ID order), all isFavorite = true.
    @Test
    public void favorites_addAndList() {
        model.setFavorite(74, true);
        model.setFavorite(5, true);
        model.setFavorite(37, true);

        List<Model.ImageResource> favs = model.favoritedImagesSearch();
        assertEquals(Arrays.asList(5, 37, 74), ids(favs));
        for (Model.ImageResource img : favs) assertTrue(img.isFavorite());
    }

    // Expected: unfavoriting 37 leaves [5, 74].
    @Test
    public void favorites_remove() {
        model.setFavorite(5, true);
        model.setFavorite(37, true);
        model.setFavorite(74, true);
        model.setFavorite(37, false);
        assertEquals(Arrays.asList(5, 74), ids(model.favoritedImagesSearch()));
    }

    // Expected: favoriting the same image twice still lists it once.
    @Test
    public void favorites_idempotent() {
        model.setFavorite(10, true);
        model.setFavorite(10, true);
        assertEquals(Arrays.asList(10), ids(model.favoritedImagesSearch()));
    }

    // Expected: favorite tags come back correctly (image 37 = ItsOver).
    @Test
    public void favorites_includeTags() {
        model.setFavorite(37, true);
        List<Model.ImageResource> favs = model.favoritedImagesSearch();
        assertEquals(1, favs.size());
        assertEquals(Arrays.asList("Sad", "Cooked", "Crashout", "L"), favs.get(0).getTags());
    }

    // Expected: favorite status shows up in tagFilteredSearch results too.
    @Test
    public void favorites_reflectedInTagSearch() {
        model.setFavorite(14, true);
        for (Model.ImageResource img : model.tagFilteredSearch(new String[]{"Cooked"})) {
            assertEquals("isFavorite for image " + img.getImageID(),
                    img.getImageID() == 14, img.isFavorite());
        }
    }

    // Expected: favorite status shows up in messageContextSearch results too.
    @Test
    public void favorites_reflectedInContextSearch() {
        model.setFavorite(75, true);
        for (Model.ImageResource img : model.messageContextSearch("it's too early for this")) {
            assertEquals("isFavorite for image " + img.getImageID(),
                    img.getImageID() == 75, img.isFavorite());
        }
    }

    // Expected: favoriting an ID that doesn't exist changes nothing and doesn't crash.
    @Test
    public void favorites_invalidIdDoesNothing() {
        model.setFavorite(999, true);
        model.setFavorite(0, true);
        assertTrue(model.favoritedImagesSearch().isEmpty());
    }

    // =================================================================================
    // 5. RANDOM CAT
    // =================================================================================

    // Expected: never null, ID always 1..90, always has tags.
    @Test
    public void random_returnsValidImage() {
        for (int i = 0; i < 50; i++) {
            Model.ImageResource img = model.randomCatImageSearch();
            assertNotNull(img);
            assertTrue(img.getImageID() >= 1 && img.getImageID() <= TOTAL_IMAGES);
            assertFalse(img.getTags().isEmpty());
        }
    }

    // Expected: 200 draws produce many different cats (at least 30 distinct IDs).
    @Test
    public void random_isActuallyRandom() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(model.randomCatImageSearch().getImageID());
        assertTrue("only " + seen.size() + " distinct IDs in 200 draws", seen.size() >= 30);
    }

    // Expected: if every image is a favorite, the random result is marked as favorite.
    @Test
    public void random_reflectsFavoriteStatus() {
        for (int id = 1; id <= TOTAL_IMAGES; id++) model.setFavorite(id, true);
        assertTrue(model.randomCatImageSearch().isFavorite());
        resetFavorites();
        assertFalse(model.randomCatImageSearch().isFavorite());
    }

    // =================================================================================
    // 6. EMBEDDING HELPERS
    // =================================================================================

    // Expected: embed returns a non-empty vector.
    @Test
    public void embed_returnsVector() {
        Embedding e = model.embed("bro ur so cooked");
        assertNotNull(e);
        assertTrue(e.floatEmbedding().length > 0);
    }

    // Expected: string format is "[a,b,c,...]" with (length - 1) commas.
    @Test
    public void embeddingToString_format() {
        Embedding e = model.embed("hello");
        String s = model.embeddingToString(e);
        assertTrue(s.startsWith("["));
        assertTrue(s.endsWith("]"));
        int commas = s.length() - s.replace(",", "").length();
        assertEquals(e.floatEmbedding().length - 1, commas);
    }

    // Expected: Embedding -> String -> Embedding gives back the same numbers,
    // and similarity with the original is 1.0.
    @Test
    public void embedding_roundTrip() {
        Embedding original = model.embed("who let bro cook");
        Embedding restored = model.stringToEmbedding(model.embeddingToString(original));

        float[] a = original.floatEmbedding();
        float[] b = restored.floatEmbedding();
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], 1e-6f);

        assertEquals(1.0, model.similarity(original, restored), 1e-4);
    }

    // Expected: stringToEmbedding tolerates spaces: "[ 1.0, 2.0 , 3.0 ]" -> {1, 2, 3}
    @Test
    public void stringToEmbedding_handlesSpaces() {
        float[] v = model.stringToEmbedding("[ 1.0, 2.0 , 3.0 ]").floatEmbedding();
        assertEquals(3, v.length);
        assertEquals(1.0f, v[0], 1e-6f);
        assertEquals(2.0f, v[1], 1e-6f);
        assertEquals(3.0f, v[2], 1e-6f);
    }

    // Expected: similarity(x, x) = 1, similarity is symmetric, and always within [-1, 1].
    @Test
    public void similarity_basicProperties() {
        Embedding a = model.embed("I'm so hungry, let's get food");
        Embedding b = model.embed("I'm going to sleep now");

        assertEquals(1.0, model.similarity(a, a), 1e-4);
        assertEquals(model.similarity(a, b), model.similarity(b, a), 1e-9);
        double s = model.similarity(a, b);
        assertTrue(s >= -1.0 - EPS && s <= 1.0 + EPS);
    }

    // Expected: related sentences score higher than unrelated ones.
    @Test
    public void similarity_relatedBeatsUnrelated() {
        Embedding hungry = model.embed("I'm starving, what's for dinner");
        Embedding food = model.embed("let's go eat, I need food now");
        Embedding sleep = model.embed("I'm so tired, going to bed");
        assertTrue(model.similarity(hungry, food) > model.similarity(hungry, sleep));
    }

    // =================================================================================
    // 7. MESSAGE CONTEXT SEARCH: behavior (should always pass)
    // =================================================================================

    // Expected: always at least 1 result, even for nonsense input.
    @Test
    public void contextSearch_neverEmpty() {
        String[] inputs = {"bro ur so cooked", "what the helllll", "asdfghjkl qwerty",
                "ok", "I failed my exam today and I don't know what to tell my parents"};
        for (String in : inputs) {
            assertFalse("empty results for: " + in, model.messageContextSearch(in).isEmpty());
        }
    }

    // Expected: results are sorted highest similarity first.
    @Test
    public void contextSearch_sortedDescending() {
        List<Model.ImageResource> r = model.messageContextSearch("I'm so tired this morning");
        for (int i = 1; i < r.size(); i++) {
            assertTrue("not sorted at index " + i,
                    r.get(i - 1).getSimilarity() >= r.get(i).getSimilarity());
        }
    }

    // Expected: no duplicate images, all IDs valid, at most 90 results.
    @Test
    public void contextSearch_noDuplicatesValidIds() {
        List<Model.ImageResource> r = model.messageContextSearch("hey bestie what's up");
        assertTrue(r.size() <= TOTAL_IMAGES);
        Set<Integer> seen = new HashSet<>();
        for (Model.ImageResource img : r) {
            assertTrue(img.getImageID() >= 1 && img.getImageID() <= TOTAL_IMAGES);
            assertTrue("duplicate id " + img.getImageID(), seen.add(img.getImageID()));
        }
    }

    // Expected threshold behavior (START = 0.50, STEP = 0.05):
    //  - if the top score is >= 0.50, every result is >= 0.50
    //  - otherwise every result is within one step (0.05) of the top score
    @Test
    public void contextSearch_respectsThreshold() {
        String[] inputs = {"it's too early for this", "asdfghjkl qwerty", "thank you bestie"};
        for (String in : inputs) {
            List<Model.ImageResource> r = model.messageContextSearch(in);
            double top = r.get(0).getSimilarity();
            double floor = (top >= 0.50) ? 0.50 : top - 0.05;
            for (Model.ImageResource img : r) {
                assertTrue("'" + in + "': image " + img.getImageID() + " score "
                                + img.getSimilarity() + " below expected floor " + floor,
                        img.getSimilarity() >= floor - 1e-9);
            }
        }
    }

    // =================================================================================
    // 8. MESSAGE CONTEXT SEARCH: quality (expected best matches)
    // These depend on the embedding model. A failure here means "tune the threshold or
    // the embedding_text," not necessarily a code bug. Each expects the listed image
    // to appear in the top 5 results.
    // =================================================================================

    private void assertInTop5(String message, int expectedId) {
        List<Model.ImageResource> r = model.messageContextSearch(message);
        List<Integer> top = ids(r.subList(0, Math.min(5, r.size())));
        assertTrue("'" + message + "' expected image " + expectedId + " in top 5, got " + top,
                top.contains(expectedId));
    }

    @Test public void quality_tooEarly()      { assertInTop5("it's too early for this, I just woke up", 75); } // TooEarlyForThis
    @Test public void quality_hungry()        { assertInTop5("I'm starving, what's for dinner", 34); }         // HungryAsShit
    @Test public void quality_lockIn()        { assertInTop5("time to lock in and study for finals", 45); }    // LockInNewYork
    @Test public void quality_bathroom()      { assertInTop5("I really need to use the bathroom", 27); }       // GottaShit
    @Test public void quality_apology()       { assertInTop5("I'm so sorry, my bad", 2); }                     // Apolocheese
    @Test public void quality_pickUpPhone()   { assertInTop5("pick up the phone, answer my call", 56); }       // PlsPickup
    @Test public void quality_thanksBestie()  { assertInTop5("thank you bestie, love you", 87); }              // XieXieTwin
    @Test public void quality_procrastinate() { assertInTop5("I'm not doing my homework, just putting it off", 60); } // Procrastination
    @Test public void quality_youUp()         { assertInTop5("are you awake? can't sleep", 4); }               // Awake
    @Test public void quality_badIdea()       { assertInTop5("who let bro cook, this is a terrible idea", 85); } // WhoLetBroCook
}