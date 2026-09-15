package cz.ctuprotebe.vyletnikviz;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class LiveGenerationSmokeTest {
    @Test public void checkpointRetainsAcceptedWorkAndRejectsDifferentPlan()throws Exception {
        java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("quiz-journal");
        JSONObject original=LiveGenerationSmoke.journal();
        original.getJSONArray("accepted").put(new JSONObject().put("slot",1));
        LiveGenerationSmoke.saveCheckpoint(dir,original);
        assertEquals(1,LiveGenerationSmoke.restore(dir.resolve("resume.json")).getJSONArray("accepted").length());
        assertFalse(new JSONObject(java.nio.file.Files.readString(dir.resolve("accepted-questions.json"))).getBoolean("complete"));
        original.getJSONArray("plan").getJSONObject(0).put("assigned_player","Changed");
        LiveGenerationSmoke.saveCheckpoint(dir,original);
        try{LiveGenerationSmoke.restore(dir.resolve("resume.json"));fail();}catch(java.io.IOException expected){}
    }
    @Test public void acceptedQuestionsArtifactIsCompleteExactlyWhenPlanIsComplete()throws Exception {
        java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("quiz-complete");
        JSONObject state=LiveGenerationSmoke.journal();
        JSONArray accepted=state.getJSONArray("accepted");
        JSONArray plan=state.getJSONArray("plan");
        for(int i=0;i<plan.length();i++)accepted.put(new JSONObject().put("slot",i+1));
        LiveGenerationSmoke.saveCheckpoint(dir,state);
        JSONObject artifact=new JSONObject(java.nio.file.Files.readString(dir.resolve("accepted-questions.json")));
        assertTrue(artifact.getBoolean("complete"));
        assertEquals(10,artifact.getJSONArray("questions").length());
    }
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
    @Test public void reportIncludesOnlySanitizedTerminalResponseState()throws Exception {
        String sentinel="fake-secret-sentinel";
        JSONObject result=LiveGenerationSmoke.safeFailure(
                new QuizGeneration.ResponseException("failed","server_error"));
        assertEquals("failed",result.getString("response_status"));
        assertEquals("server_error",result.getString("response_reason"));
        assertFalse(result.toString().contains(sentinel));
    }
}
