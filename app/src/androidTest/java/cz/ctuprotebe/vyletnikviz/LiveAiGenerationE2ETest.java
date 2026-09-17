package cz.ctuprotebe.vyletnikviz;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Log;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.FileWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LiveAiGenerationE2ETest {
    private static final String TAG = "LiveAiE2E";
    private static final String REQUEST_ID = "vk27_android_ai_live_20260917_004";

    @Test public void androidGeneratesValidQuizAndCompletesHumanStyleTripGame() throws Exception {
        JSONArray players = new JSONArray()
                .put(new JSONObject().put("name", "Barča").put("topic", "Psychologie").put("color", 1))
                .put(new JSONObject().put("name", "Dominik").put("topic", "Lední hokej").put("color", 2));

        JSONObject context = new JSONObject()
                .put("players", players)
                .put("title", "E2E test výletního kvízu")
                .put("date", "17. 09. 2026")
                .put("note", "Jednorázový produkční test Android → backend → AI → kontrola → celá hra")
                .put("photos", new JSONArray())
                .put("count", 10)
                .put("diff", "Vyvážená")
                .put("cats", new JSONArray().put("Česko").put("Svět").put("Historie").put("Příroda").put("Věda a technika").put("Kultura"))
                .put("strong", true)
                .put("strongEvery", 3)
                .put("bizarre", true)
                .put("bizarreEvery", 5)
                .put("sound", false);

        String[] topics = {
                "Historie – Česko",
                "Příroda – svět",
                "Věda a technika – Česko",
                "Kultura – svět",
                "Bizarní svět – pravdivý překvapivý fakt",
                "Zeměpis – Česko",
                "Historie – svět",
                "Gastronomie – Česko",
                "Psychologie",
                "Lední hokej"
        };
        JSONArray plan = new JSONArray();
        for (int i = 0; i < 10; i++) {
            plan.put(new JSONObject()
                    .put("slot", i + 1)
                    .put("assigned_player", i % 2 == 0 ? "Barča" : "Dominik")
                    .put("requested_topic", topics[i])
                    .put("mode", i == 4 ? "bizarni" : (i >= 8 ? "silny_okruh" : "vseobecny"))
                    .put("hard", i == 5 || i >= 8)
                    .put("difficulty", "Vyvážená"));
        }

        JSONObject journal = new JSONObject()
                .put("version", 27)
                .put("request_id", REQUEST_ID)
                .put("as_of", "2026-09-17")
                .put("context", context)
                .put("plan", plan);

        ServerQuizClient client = new ServerQuizClient(journal, () -> {}, text -> Log.i(TAG, text));
        JSONObject result = client.generate();
        JSONArray questions = result.getJSONArray("questions");
        assertEquals(10, questions.length());

        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            assertEquals(i + 1, q.getInt("slot"));
            assertEquals(4, q.getJSONArray("options").length());
            assertTrue(q.getInt("correct") >= 0 && q.getInt("correct") <= 3);
            assertTrue(q.getString("question").trim().length() >= 12);
            assertTrue(q.getString("question").trim().length() <= 260);
            assertTrue(q.getString("explanation").trim().length() >= 20);
            String answer = q.getJSONArray("options").getString(q.getInt("correct"));
            Log.i(TAG, "Q" + (i + 1) + ": " + q.getString("question") + " | správně: " + answer + " | " + q.getString("explanation"));
        }

        Context app = ApplicationProvider.getApplicationContext();
        assertTrue(app.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit().clear().commit());
        JSONObject humanEvidence = new JSONObject();

        try (ActivityScenario<MainActivity27> scenario = ActivityScenario.launch(MainActivity27.class)) {
            scenario.onActivity(a -> {
                try {
                    a.restoreGenerationContext(new JSONObject(context.toString()));
                    a.parse(new JSONObject(result.toString()));
                    a.cfg.personalized = true;
                    a.start();

                    assertEquals(10, a.quiz.size());
                    assertEquals(0, a.game.index);
                    assertEquals("Barča", a.players.get(a.game.owner).name);

                    int strongSpecials = 0;
                    int bizarreSpecials = 0;
                    for (int i = 0; i < a.quiz.size(); i++) {
                        String special = a.quiz.get(i).special;
                        if (special.contains("Silný okruh")) strongSpecials++;
                        if (special.contains("Bizarní svět")) bizarreSpecials++;
                    }
                    assertTrue("AI hra má obsahovat osobní silný okruh", strongSpecials > 0);
                    assertTrue("AI hra má obsahovat bizarní pravdivou otázku", bizarreSpecials > 0);

                    for (int i = 0; i < 10; i++) {
                        assertFalse(a.gameFinished);
                        assertEquals(i, a.game.index);
                        assertEquals(i % 2, a.game.owner);
                        int correct = a.quiz.get(i).correct;

                        // Jako skutečná parta: někdy správně napoprvé, někdy chyba a převzetí,
                        // jindy hráč řekne „nevím“ a pošle otázku dál.
                        if (i % 4 == 0) {
                            int wrong = (correct + 1) % 4;
                            a.answer(wrong);
                            assertFalse(a.game.answered);
                            assertEquals(1, a.game.attempt);
                            assertNotEquals(a.game.owner, a.game.responder);
                            a.answer(correct);
                        } else if (i % 4 == 2) {
                            a.pass();
                            assertFalse(a.game.answered);
                            assertEquals(1, a.game.attempt);
                            assertNotEquals(a.game.owner, a.game.responder);
                            a.answer(correct);
                        } else {
                            a.answer(correct);
                        }

                        assertTrue(a.game.answered);
                        assertTrue(a.quiz.get(i).explanation.trim().length() >= 20);
                        a.next();
                    }

                    assertTrue(a.gameFinished);
                    int totalSteals = 0;
                    for (int steals : a.game.steals) totalSteals += steals;
                    assertTrue("Při simulaci musí vzniknout alespoň jedno převzetí", totalSteals > 0);
                    assertFalse(a.prefs.contains("resume"));

                    JSONArray history = new JSONArray(a.prefs.getString("history", "[]"));
                    assertEquals(1, history.length());
                    JSONObject trip = history.getJSONObject(0);
                    assertEquals("E2E test výletního kvízu", trip.getString("title"));
                    assertEquals(10, trip.getJSONArray("questions").length());
                    assertFalse(trip.optString("winner").trim().isEmpty());
                    assertEquals(2, trip.getJSONArray("players").length());

                    humanEvidence.put("game_finished", true);
                    humanEvidence.put("questions_played", 10);
                    humanEvidence.put("strong_specials", strongSpecials);
                    humanEvidence.put("bizarre_specials", bizarreSpecials);
                    humanEvidence.put("steals", totalSteals);
                    humanEvidence.put("winner", trip.optString("winner"));
                    humanEvidence.put("history_entries", history.length());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        File dir = new File(app.getExternalFilesDir(null), "test-screens");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileWriter writer = new FileWriter(new File(dir, "ai-e2e.json"))) {
            writer.write(result.toString(2));
        }
        try (FileWriter writer = new FileWriter(new File(dir, "human-trip-playthrough.json"))) {
            writer.write(humanEvidence.toString(2));
        }
    }
}
