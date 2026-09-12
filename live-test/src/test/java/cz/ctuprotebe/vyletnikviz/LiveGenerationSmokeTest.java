package cz.ctuprotebe.vyletnikviz;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class LiveGenerationSmokeTest {
    @Test public void fixtureHasTenOrderedSlotsIncludingReportedProblemTopics()throws Exception {
        JSONArray plan=LiveGenerationSmoke.journal().getJSONArray("plan");assertEquals(10,plan.length());
        assertEquals("Český rap",plan.getJSONObject(3).getString("requested_topic"));
        for(int i=0;i<10;i++)assertEquals(i+1,plan.getJSONObject(i).getInt("slot"));
    }
    @Test public void budgetStopsBeforeFifthPost()throws Exception {
        LiveGenerationSmoke.Counters c=new LiveGenerationSmoke.Counters();
        for(int i=0;i<4;i++)c.beforePost();
        try{c.beforePost();fail();}catch(LiveGenerationSmoke.BudgetExceeded expected){}
        assertEquals(4,c.posts);
    }
    @Test public void reportNeverIncludesArbitraryExceptionText()throws Exception {
        String sentinel="fake-secret-sentinel";
        JSONObject result=LiveGenerationSmoke.safeFailure(new java.io.IOException(sentinel));
        assertFalse(result.toString().contains(sentinel));assertFalse(result.getBoolean("passed"));
    }
}
