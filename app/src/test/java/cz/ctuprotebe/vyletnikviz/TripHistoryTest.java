package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TripHistoryTest {
    static JSONObject trip() throws Exception {
        return new JSONObject().put("id",123).put("title","Okoř").put("date","25. 6. 2026")
                .put("players",new JSONArray().put(new JSONObject().put("name","Barča").put("score",1))
                        .put(new JSONObject().put("name","Dominik").put("score",.6)))
                .put("questions",new JSONArray().put(QuizGenerationTest.question(1)));
    }
    @Test public void importsCompleteRecordAndDerivesWinnerFromScores() throws Exception {
        JSONObject t=trip().put("winner","Někdo jiný");
        JSONObject result=TripHistory.parse(TripHistory.PREFIX+t);
        assertEquals("Barča",result.getString("winner"));
        assertEquals(QuizGenerationTest.URL,result.getJSONArray("questions").getJSONObject(0).getJSONArray("sources").getJSONObject(0).getString("url"));
    }
    @Test public void malformedEmptyAndOversizedImportsAreRejected() throws Exception {
        assertThrows(Exception.class,()->TripHistory.parse(TripHistory.PREFIX+"{}"));
        assertThrows(Exception.class,()->TripHistory.parse("nějaký jiný text"));
        assertThrows(Exception.class,()->TripHistory.parse(TripHistory.PREFIX+new String(new char[2_000_001])));
    }
    @Test public void invalidScoresDuplicateNamesAndBrokenOptionsAreRejected() throws Exception {
        JSONObject a=trip();a.getJSONArray("players").getJSONObject(1).put("score",-1);
        assertThrows(Exception.class,()->TripHistory.parse(TripHistory.PREFIX+a));
        JSONObject b=trip();b.getJSONArray("players").getJSONObject(1).put("name","BARČA");
        assertThrows(Exception.class,()->TripHistory.parse(TripHistory.PREFIX+b));
        JSONObject c=trip();c.getJSONArray("questions").getJSONObject(0).put("correct",4);
        assertThrows(Exception.class,()->TripHistory.parse(TripHistory.PREFIX+c));
    }
    @Test public void recognizesExistingTripWithoutCountingItAgain() throws Exception {
        assertTrue(TripHistory.contains(new JSONArray().put(trip()),123));
        assertFalse(TripHistory.contains(new JSONArray().put(trip()),124));
    }
}
