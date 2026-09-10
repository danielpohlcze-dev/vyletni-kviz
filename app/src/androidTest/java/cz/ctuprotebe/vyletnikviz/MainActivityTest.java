package cz.ctuprotebe.vyletnikviz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
            onView(withText("+ Přidat hráče")).perform(scrollTo(), click());
            onView(withText("Maximum je 5 hráčů")).check(matches(isDisplayed()));
            onView(withText("＋ Přidat fotky (max. 5)")).check(matches(isDisplayed()));
        }
    }

    @Test public void photoButtonReallyLaunchesAndroidDocumentPicker() {
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
            onView(withText("Hrát všeobecný kvíz")).perform(click());
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
}
