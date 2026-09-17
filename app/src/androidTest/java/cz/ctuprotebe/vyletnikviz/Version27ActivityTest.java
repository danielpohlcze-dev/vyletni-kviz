package cz.ctuprotebe.vyletnikviz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Version27ActivityTest {
    @Before public void reset() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void launcherUsesServerBackedConnectionScreenWithoutPhoneApiKey() {
        try (ActivityScenario<MainActivity27> ignored = ActivityScenario.launch(MainActivity27.class)) {
            onView(withText("⛰  VÝLETNÍ KVÍZ")).check(matches(isDisplayed()));
            onView(withText("⚙  Připojení k AI")).perform(click());
            onView(withText("PŘIPOJENÍ K AI")).check(matches(isDisplayed()));
            onView(withText("✓ V telefonu není potřeba OpenAI API klíč")).check(matches(isDisplayed()));
            onView(withText("Tvorba i kontrola kvízu probíhá na serveru.")).check(matches(isDisplayed()));
            onView(withText("Sporné otázky se nevydají do hry. Opravují se jen problematické kusy, nejvýše ve třech cílených kolech.")).check(matches(isDisplayed()));
        }
    }
}
