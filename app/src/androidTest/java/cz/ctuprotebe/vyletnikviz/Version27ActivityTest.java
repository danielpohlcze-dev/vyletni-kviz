package cz.ctuprotebe.vyletnikviz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Version27ActivityTest {
    @Before public void reset() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void launcherShowsPrimaryAndBackupAiProviders() {
        try (ActivityScenario<MainActivity27> ignored = ActivityScenario.launch(MainActivity27.class)) {
            onView(withText("⛰  VÝLETNÍ KVÍZ")).check(matches(isDisplayed()));
            onView(withText("⚙  Připojení k AI")).perform(click());
            onView(withText("PŘIPOJENÍ K AI")).check(matches(isDisplayed()));
            onView(withText("1. AppDeploy • primární zdroj")).check(matches(isDisplayed()));
            onView(withText("2. OpenAI API • záložní klíč zatím chybí")).check(matches(isDisplayed()));
            onView(withText("AppDeploy + volitelná OpenAI záloha. Přepnutí vždy potvrzujete.")).check(matches(isDisplayed()));
            onView(withText(containsString("Nikdy sama nepřepne poskytovatele"))).check(matches(isDisplayed()));
        }
    }

    @Test public void appDeployCreditFailureAsksBeforeAnySwitch() {
        try (ActivityScenario<MainActivity27> scenario = ActivityScenario.launch(MainActivity27.class)) {
            scenario.onActivity(a -> a.showProviderError(
                    new ServerQuizClient.ServerException(402, "", "AppDeploy HTTP 402")));
            onView(withText("AppDeploy má kreditní limit")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Nastavit OpenAI zálohu")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Později")).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }

    @Test public void openAiCreditFailureAsksBeforeReturningToAppDeploy() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("quiz", Context.MODE_PRIVATE).edit()
                .putString("ai_pending", new JSONObject().put("provider", MainActivity27.PROVIDER_OPENAI).toString())
                .commit();
        try (ActivityScenario<MainActivity27> scenario = ActivityScenario.launch(MainActivity27.class)) {
            scenario.onActivity(a -> a.showProviderError(
                    new OpenAiTransport.ApiException(429, "insufficient_quota",
                            OpenAiTransport.statusMessage(429, "insufficient_quota"))));
            onView(withText("OpenAI API má kreditní limit")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Zkusit AppDeploy")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Zůstat na OpenAI")).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
}
