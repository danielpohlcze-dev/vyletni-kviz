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
            onView(withText("+ Přidat hráče")).perform(scrollTo(), safeClick());
            onView(withText("+ Přidat hráče")).perform(scrollTo(), safeClick());
            onView(withText("+ Přidat hráče")).perform(scrollTo(), safeClick());
            ignored.onActivity(a -> assertEquals(5, countPlayerNames(a.findViewById(android.R.id.content))));
            onView(withText("＋ Přidat fotky (max. 5)")).perform(scrollTo()).check(matches(isDisplayed()));
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
            onView(withText("Zvuky a jemné vibrace")).perform(scrollTo(), safeClick());
            onView(withText("Připravit offline kvíz")).perform(scrollTo(), safeClick());
            onView(withText("Hrát všeobecný kvíz")).perform(click());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            onView(withText("Neví – předat hráči Dominik")).perform(scrollTo(), safeClick());
            onView(withText(startsWith("Přebírá: Dominik"))).check(matches(isDisplayed()));
            onView(withText(containsString("pokus 2 z 2"))).check(matches(isDisplayed()));
            onView(withText("↶ Zpět")).perform(scrollTo(), safeClick());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            onView(withText("Vpřed ↷")).perform(scrollTo(), safeClick());
            onView(withText(startsWith("Přebírá: Dominik"))).check(matches(isDisplayed()));
            onView(withText("Neví – ukázat odpověď")).perform(scrollTo(), safeClick());
            onView(withText(startsWith("PROČ:"))).check(matches(isDisplayed()));
        }
    }

    @Test public void chronicleAndAiSettingsOpenWithoutData() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("📖  Kronika výletů")).perform(click());
            onView(withText("Zatím tu není žádný odehraný výlet.")).check(matches(isDisplayed()));
            onView(withText("Zpět")).perform(scrollTo(), safeClick());
            onView(withText("⚙  Připojení k AI")).perform(click());
            onView(withText("OpenAI klíč zatím není uložený")).check(matches(isDisplayed()));
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

    // Espresso's coordinate click could hit the emulator's bottom HOME bar after a long scroll.
    // performClick still exercises the real Android listener while avoiding that emulator artefact.
    private static ViewAction safeClick() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isEnabled(); }
            @Override public String getDescription() { return "safe programmatic click"; }
            @Override public void perform(UiController controller, View view) {
                view.performClick();
                controller.loopMainThreadUntilIdle();
            }
        };
    }
}
