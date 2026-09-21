package cz.ctuprotebe.vyletnikviz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class TripExperienceAuditTest {
    @Before public void reset() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void firstTimeUserCanEnjoyACompleteTripFromHomeToChronicle() {
        try (ActivityScenario<MainActivity27> scenario = ActivityScenario.launch(MainActivity27.class)) {
            onView(withText("⛰  VÝLETNÍ KVÍZ")).check(matches(isDisplayed()));
            screenshot("ux-01-home");

            onView(withText("＋  Začít nový výlet")).perform(click());
            onView(withText("NOVÝ VÝLET")).check(matches(isDisplayed()));
            onView(withHint("Název, například Okoř")).perform(replaceText("Podzimní Haná"), closeSoftKeyboard());
            onView(withHint("Poznámka nebo hláška z výletu")).perform(replaceText("Výlet, na který chceme vzpomínat"), closeSoftKeyboard());
            screenshot("ux-02-trip-setup-top");

            onView(withText("Připravit offline kvíz")).perform(scrollTo(), click());
            onView(withText("Offline kvíz")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Hrát všeobecný kvíz")).inRoot(isDialog()).perform(click());

            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            screenshot("ux-03-first-question");

            // První otázku projdeme jako skutečná parta: Barča neví, Dominik přebírá,
            // potom se odpověď odhalí. Ověříme tak převzetí i vysvětlení.
            onView(withText("Neví – předat hráči Dominik")).perform(scrollTo(), click());
            onView(withText(startsWith("Přebírá: Dominik"))).check(matches(isDisplayed()));
            onView(withText("Neví – ukázat odpověď")).perform(scrollTo(), click());
            onView(withText(startsWith("PROČ:"))).perform(scrollTo()).check(matches(isDisplayed()));
            screenshot("ux-04-reveal-and-explanation");
            onView(withText("Další otázka")).perform(scrollTo(), click());

            // Zbytek hry projdeme rychle, ale stále přes stejné prvky, které používá člověk.
            for (int i = 1; i < 10; i++) {
                onView(withText("Ukončit otázku a ukázat odpověď")).perform(scrollTo(), click());
                onView(withText(startsWith("PROČ:"))).perform(scrollTo()).check(matches(isDisplayed()));
                if (i < 9) {
                    onView(withText("Další otázka")).perform(scrollTo(), click());
                } else {
                    onView(withText("Zobrazit výsledky")).perform(scrollTo(), click());
                }
            }

            onView(withText("VÝSLEDKY")).check(matches(isDisplayed()));
            onView(withText(containsString("Hra je uložená v kronice výletů"))).check(matches(isDisplayed()));
            screenshot("ux-05-results");

            onView(withText("Otevřít kroniku")).perform(scrollTo(), click());
            onView(withText("KRONIKA VÝLETŮ")).check(matches(isDisplayed()));
            onView(withText("Podzimní Haná")).check(matches(isDisplayed()));
            onView(withText("„Výlet, na který chceme vzpomínat“")).check(matches(isDisplayed()));
            screenshot("ux-06-chronicle");

            onView(withText("Podzimní Haná")).perform(click());
            onView(withText("🗑 Odebrat výlet z kroniky")).perform(scrollTo(), click());
            onView(withText("Odebrat výlet z kroniky?")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Odebrat")).inRoot(isDialog()).perform(click());
            onView(withText("KRONIKA VÝLETŮ")).check(matches(isDisplayed()));
            onView(withText("Zatím tu není žádný odehraný výlet.")).check(matches(isDisplayed()));
            screenshot("ux-06b-chronicle-after-delete");
        }
    }

    @Test public void saveResumeAndServerConnectionFeelNaturalOnTheRealLauncher() {
        try (ActivityScenario<MainActivity27> scenario = ActivityScenario.launch(MainActivity27.class)) {
            onView(withText("⚡  Rychlá hra ve dvou")).perform(click());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            onView(withText("Uložit rozehranou hru a skončit")).perform(scrollTo(), click());
            onView(withText("Přerušit hru?")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Uložit a skončit")).inRoot(isDialog()).perform(click());

            onView(withText("▶  Pokračovat v rozehrané hře")).check(matches(isDisplayed())).perform(click());
            onView(withText(startsWith("Na tahu: Barča"))).check(matches(isDisplayed()));
            screenshot("ux-07-resumed-game");

            // Vrátíme se domů přes bezpečné uložení a zkontrolujeme skutečnou 2.7.3 obrazovku připojení.
            onView(withText("Uložit rozehranou hru a skončit")).perform(scrollTo(), click());
            onView(withText("Uložit a skončit")).inRoot(isDialog()).perform(click());
            onView(withText("⚙  Připojení k AI")).perform(click());
            onView(withText("PŘIPOJENÍ K AI")).check(matches(isDisplayed()));
            onView(withText("1. AppDeploy • primární zdroj")).check(matches(isDisplayed()));
            onView(withText("2. OpenAI API • záložní klíč zatím chybí")).check(matches(isDisplayed()));
            onView(withText(containsString("Pokud AppDeploy vrátí HTTP 402"))).check(matches(isDisplayed()));
            screenshot("ux-08-server-connection");
        }
    }

    private static void screenshot(String name) {
        try {
            Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
            File folder = new File(target.getExternalFilesDir(null), "test-screens");
            if (!folder.exists()) folder.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
        } catch (Exception ignored) { }
    }
}
