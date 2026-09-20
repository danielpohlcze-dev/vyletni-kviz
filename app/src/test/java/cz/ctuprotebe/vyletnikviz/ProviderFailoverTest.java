package cz.ctuprotebe.vyletnikviz;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProviderFailoverTest {
    @Test public void switchingProviderKeepsQuizPlanButClearsProviderSpecificWork() throws Exception {
        JSONObject journal = new JSONObject()
                .put("version", 27)
                .put("provider", MainActivity27.PROVIDER_APPDEPLOY)
                .put("request_id", "vk27_original_123456")
                .put("context", new JSONObject().put("title", "Výlet"))
                .put("plan", new JSONArray().put(new JSONObject().put("slot", 1)))
                .put("pending_response", new JSONObject().put("id", "resp_old"))
                .put("pending_result", new JSONObject())
                .put("draft", new JSONObject())
                .put("accepted", new JSONArray().put(new JSONObject()))
                .put("feedback", "old")
                .put("events", new JSONArray())
                .put("batch_size", 5)
                .put("server_result", new JSONObject())
                .put("provider_switch_count", 1);

        MainActivity27.prepareProviderSwitch(journal, MainActivity27.PROVIDER_OPENAI);

        assertEquals(MainActivity27.PROVIDER_OPENAI, journal.getString("provider"));
        assertEquals("Výlet", journal.getJSONObject("context").getString("title"));
        assertEquals(1, journal.getJSONArray("plan").length());
        assertEquals(0, journal.getJSONArray("accepted").length());
        assertEquals(2, journal.getInt("provider_switch_count"));
        assertEquals("appdeploy_to_openai", journal.getString("last_switch"));
        assertNotEquals("vk27_original_123456", journal.getString("request_id"));
        for (String key : new String[]{
                "pending_response", "pending_result", "draft", "feedback",
                "events", "batch_size", "server_result"
        }) assertFalse(key, journal.has(key));
    }

    @Test public void providerSwitchRejectsUnknownTarget() throws Exception {
        JSONObject journal = new JSONObject()
                .put("provider", MainActivity27.PROVIDER_APPDEPLOY)
                .put("context", new JSONObject())
                .put("plan", new JSONArray());
        assertThrows(org.json.JSONException.class,
                () -> MainActivity27.prepareProviderSwitch(journal, "mystery"));
    }

    @Test public void onlyAppDeploy402CountsAsConfirmedAppDeployCreditSignal() {
        assertTrue(MainActivity27.isAppDeployCreditLimit(
                new ServerQuizClient.ServerException(402, "APP_TEMPORARILY_UNAVAILABLE", "limit")));
        assertFalse(MainActivity27.isAppDeployCreditLimit(
                new ServerQuizClient.ServerException(429, "rate_limit", "busy")));
        assertFalse(MainActivity27.isAppDeployCreditLimit(
                new ServerQuizClient.ServerException(503, "", "down")));
    }
}
