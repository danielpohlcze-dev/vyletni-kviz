package cz.ctuprotebe.vyletnikviz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;

import org.hamcrest.Matcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Before public void resetApp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After public void cleanIntents() {
        try { Intents.release(); } catch (IllegalStateException ignored) { }
    }

    @Test public void homeSetupAndFivePlayersAreReachable() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("⛰  VÝLETNÍ KVÍZ")).check(matches(isDisplayed()));
            onView(withText("＋  Začít nový výlet")).perform(click());
            onView(withText("NOVÝ VÝLET")).check(matches(isDisplayed()));
            onView(withText("+ Přidat hráče")).perform(scrollTo(), click());
            onView(withText("+ Přidat hráče")).perform(scrollTo(), click());
            onView(withText("+ Přidat hráče")).perform(scrollTo(), click());
            ignored.onActivity(a -> assertEquals(5, countPlayerNames(a.findViewById(android.R.id.content))));
            onView(withText("＋ Přidat fotky (max. 5)")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void photoButtonRequestsAndroidDocumentPicker() {
        Intents.init();
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null));
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("＋  Začít nový výlet")).perform(click());
            onView(withText("＋ Přidat fotky (max. 5)")).perform(scrollTo(), click());
            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT));
        }
    }

    @Test public void offlineGamePassUndoRedoAndRevealWork() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("＋  Začít nový výlet")).perform(click());
            onView(withText("Zvuky a jemné vibrace")).perform(scrollTo(), click());
            onView(withText("Připravit offline kvíz")).perform(scrollTo(), click());
            onView(withText("Hrát všeobecný kvíz")).inRoot(isDialog()).perform(click());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            onView(withText("Neví – předat hráči Dominik")).perform(scrollTo(), click());
            onView(withText(startsWith("Přebírá: Dominik"))).check(matches(isDisplayed()));
            onView(withText(containsString("pokus 2 z 2"))).check(matches(isDisplayed()));
            onView(withText("↶ Zpět")).perform(scrollTo(), click());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            onView(withText("Vpřed ↷")).perform(scrollTo(), click());
            onView(withText(startsWith("Přebírá: Dominik"))).check(matches(isDisplayed()));
            onView(withText("Neví – ukázat odpověď")).perform(scrollTo(), click());
            onView(withText(startsWith("PROČ:"))).check(matches(isDisplayed()));
        }
    }

    @Test public void chronicleAndAiSettingsOpenWithoutData() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("📖  Kronika výletů")).perform(click());
            onView(withText("Zatím tu není žádný odehraný výlet.")).check(matches(isDisplayed()));
            onView(withText("Zpět")).perform(scrollTo(), click());
            onView(withText("⚙  Připojení k AI")).perform(click());
            onView(withText("OpenAI klíč zatím není uložený")).check(matches(isDisplayed()));
        }
    }

    @Test public void personalTopicsAndDifficultyStayWithEachOfFivePlayers() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                try {
                    a.cfg = new MainActivity.Config(); a.cfg.count = 40;
                    a.cfg.cats.add("Česko"); a.cfg.cats.add("Svět"); a.cfg.cats.add("Historie");
                    a.players.clear();
                    String[] names = {"Barča", "Dominik", "Daneček", "Kája", "Honzík"};
                    String[] topics = {"Český rap", "Slovenská politika po roce 2020", "Anglická slovíčka", "Hokej", "Vlaky"};
                    for (int i = 0; i < 5; i++) a.players.add(new MainActivity.Player(names[i], topics[i], a.BLUE));
                    org.json.JSONArray plan = a.generationPlan();
                    for (int i = 0; i < 40; i++) {
                        org.json.JSONObject slot = plan.getJSONObject(i);
                        assertEquals(names[i % 5], slot.getString("assigned_player"));
                        if ((i / 5 + 1) % 3 == 0) {
                            assertEquals(topics[i % 5], slot.getString("requested_topic"));
                            assertEquals("silny_okruh", slot.getString("mode"));
                        }
                    }
                    org.json.JSONObject context = a.generationContext();
                    a.cfg = null; a.players.clear(); a.restoreGenerationContext(context);
                    assertEquals(5, a.players.size()); assertEquals(40, a.cfg.count);
                    assertEquals("Český rap", a.players.get(0).topic);
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }

    @Test public void questionSourcesSurviveSavedGameAndAppearOnlyAfterReveal() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                try {
                    a.quick(); a.cfg.sound = false;
                    org.json.JSONObject q = a.quiz.get(0).json();
                    q.put("sources", new org.json.JSONArray().put(new org.json.JSONObject()
                            .put("url", "https://example.org/test-fixture").put("title", "Testovací zdroj")
                            .put("support", "Simulovaný zdroj pro test uložení.")));
                    q.put("requested_topic", "Historie");
                    a.parse(new org.json.JSONObject().put("questions", new org.json.JSONArray().put(q)));
                    a.start(); a.game.answered = true; a.question(); a.resume();
                    assertEquals("Historie", a.quiz.get(0).json().getString("requested_topic"));
                    assertEquals(1, a.quiz.get(0).sources.length());
                } catch (Exception e) { throw new AssertionError(e); }
            });
            onView(withText("↗ Testovací zdroj")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    private static int countPlayerNames(View view) {
        int count = view instanceof EditText && "Jméno hráče".contentEquals(((EditText) view).getHint()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countPlayerNames(group.getChildAt(i));
        }
        return count;
    }

}
