package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import org.junit.Test;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class QuizGenerationTest {
    static final String URL = "https://example.org/test-fixture";
    static JSONObject slot(int n) throws Exception {
        return new JSONObject().put("slot",n).put("assigned_player",n%2==1?"Barča":"Dominik")
                .put("requested_topic","Český rap").put("hard",false);
    }
    static JSONArray sources() throws Exception {
        return new JSONArray().put(new JSONObject().put("url",URL).put("title","Testovací zdroj")
                .put("support","Testovací doklad použitý pouze v simulované odpovědi API."));
    }
    static JSONObject question(int n) throws Exception {
        return slot(n).put("question","Která možnost patří k testovacímu případu číslo " + n + "?")
                .put("options",new JSONArray(Arrays.asList("První","Druhá","Třetí","Čtvrtá")))
                .put("correct",1).put("explanation","Toto je testovací vysvětlení pro automatickou kontrolu formátu.")
                .put("sources",sources());
    }
    static JSONObject review(int n, boolean ok) throws Exception {
        return new JSONObject().put("slot",n).put("answer_index",1).put("topic_match",ok)
                .put("unambiguous",true).put("fact_supported",true).put("explanation_supported",true)
                .put("appropriate_difficulty",true).put("reason",ok?"Testovací posudek prošel.":"Otázka o anglickém slovu nesouvisí s českým rapem.")
                .put("sources",sources());
    }
    static JSONObject response(String field, JSONArray content) throws Exception {
        return new JSONObject().put("id","resp_fixture").put("status","completed").put("output",new JSONArray()
                .put(new JSONObject().put("type","web_search_call").put("status","completed").put("action",
                        new JSONObject().put("type","search").put("sources",new JSONArray().put(new JSONObject().put("url",URL)))))
                .put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject()
                        .put("type","output_text").put("text",new JSONObject().put(field,content).toString())))));
    }
    static JSONObject journal(int count) throws Exception {
        JSONArray plan=new JSONArray();for(int i=1;i<=count;i++)plan.put(slot(i));
        return new JSONObject().put("plan",plan).put("accepted",new JSONArray()).put("as_of","2026-09-12");
    }
    static boolean isReview(JSONObject body) throws Exception {
        return body.getJSONObject("text").getJSONObject("format").getString("name").equals("quiz_review");
    }
    @Test public void acceptsOnlyCompletedSearchBackedQuizAndPreservesMetadata() throws Exception {
        JSONObject j=journal(2);AtomicInteger calls=new AtomicInteger();
        JSONObject result=new QuizGeneration(j,b->{calls.incrementAndGet();return isReview(b)
                ?response("reviews",new JSONArray().put(review(1,true)).put(review(2,true)))
                :response("questions",new JSONArray().put(question(1)).put(question(2)));},()->{},t->{}).run();
        assertEquals(2,calls.get());assertEquals(2,result.getJSONArray("questions").length());
        assertEquals(URL,result.getJSONArray("questions").getJSONObject(0).getJSONArray("sources").getJSONObject(0).getString("url"));
        assertEquals(QuizGeneration.MODEL,result.getJSONArray("questions").getJSONObject(0).getString("model"));
    }
    @Test public void repairsWrongTopicWithoutDiscardingAcceptedQuestion() throws Exception {
        JSONObject j=journal(2);AtomicInteger calls=new AtomicInteger();
        new QuizGeneration(j,b->{int n=calls.incrementAndGet();
            if(n==1)return response("questions",new JSONArray().put(question(1)).put(question(2)));
            if(n==2)return response("reviews",new JSONArray().put(review(1,true)).put(review(2,false)));
            JSONObject payload=new JSONObject(b.getJSONArray("input").getJSONObject(1).getString("content"));
            assertEquals(1,payload.getJSONArray("slots").length());
            assertEquals(2,payload.getJSONArray("slots").getJSONObject(0).getInt("slot"));
            return isReview(b)?response("reviews",new JSONArray().put(review(2,true))):response("questions",new JSONArray().put(question(2)));
        },()->{},t->{}).run();assertEquals(4,calls.get());
    }
    @Test public void cannotPlayWhenIndependentAnswerDisagrees() throws Exception {
        JSONObject j=journal(1);AtomicInteger calls=new AtomicInteger();
        assertThrows(QuizGeneration.QualityException.class,()->new QuizGeneration(j,b->{calls.incrementAndGet();
            return isReview(b)?response("reviews",new JSONArray().put(review(1,true).put("answer_index",3)))
                    :response("questions",new JSONArray().put(question(1)));},()->{},t->{}).run());
        assertEquals(0,j.getJSONArray("accepted").length());assertTrue(calls.get()<=6);
    }
    @Test public void reviewNetworkFailureResumesSavedDraftAfterProcessRestart() throws Exception {
        JSONObject j=journal(1);List<String> disk=new ArrayList<>();
        assertThrows(UnknownHostException.class,()->new QuizGeneration(j,b->{if(isReview(b))throw new UnknownHostException();
            return response("questions",new JSONArray().put(question(1)));},()->{disk.clear();disk.add(j.toString());},t->{}).run());
        JSONObject restored=new JSONObject(disk.get(0));assertNotNull(restored.optJSONObject("draft"));
        AtomicInteger calls=new AtomicInteger();
        new QuizGeneration(restored,b->{assertTrue(isReview(b));calls.incrementAndGet();return response("reviews",new JSONArray().put(review(1,true)));},()->{},t->{}).run();
        assertEquals(1,calls.get());
    }
    @Test public void refusesFabricatedUrlEvenWithValidLookingInstitutionName() throws Exception {
        JSONObject q=question(1);q.getJSONArray("sources").getJSONObject(0).put("url","https://example.org/invented");
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.validateQuestion(q,slot(1),Collections.singleton(URL),new HashSet<>()));
    }
    @Test public void rejectsMissingSearchAndIncompleteOrRefusedOutput() throws Exception {
        JSONObject r=response("questions",new JSONArray().put(question(1)));
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.decode(new JSONObject(r.toString()).put("status","incomplete")));
        r.getJSONArray("output").remove(0);
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.sourceUrls(r));
        r.getJSONArray("output").getJSONObject(0).getJSONArray("content").getJSONObject(0).put("type","refusal");
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.decode(r));
    }
    @Test public void rejectsDuplicateOptionsWrongOwnerAndWrongDifficulty() throws Exception {
        JSONObject q=question(1);q.getJSONArray("options").put(2,"  DRUHÁ ");
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.validateQuestion(q,slot(1),Collections.singleton(URL),new HashSet<>()));
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.validateQuestion(question(1).put("assigned_player","Dominik"),slot(1),Collections.singleton(URL),new HashSet<>()));
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.validateQuestion(question(1).put("hard",true),slot(1),Collections.singleton(URL),new HashSet<>()));
    }
    @Test public void refusesDuplicateQuestionsAcrossBatches() throws Exception {
        JSONObject q=question(1);Set<String> seen=new HashSet<>();seen.add(QuizGeneration.normalize(q.getString("question")));
        assertThrows(QuizGeneration.QualityException.class,()->QuizGeneration.validateQuestion(q,slot(1),Collections.singleton(URL),seen));
    }
    @Test public void batchesFortyQuestionsWithoutLosingSlots() throws Exception {
        JSONObject j=journal(40);AtomicInteger calls=new AtomicInteger();
        JSONObject result=new QuizGeneration(j,b->{calls.incrementAndGet();
            JSONObject payload=new JSONObject(b.getJSONArray("input").getJSONObject(1).getString("content"));
            JSONArray slots=payload.getJSONArray("slots"),out=new JSONArray();assertTrue(slots.length()<=5);
            for(int i=0;i<slots.length();i++){int slot=slots.getJSONObject(i).getInt("slot");out.put(isReview(b)?review(slot,true):question(slot));}
            return response(isReview(b)?"reviews":"questions",out);
        },()->{},t->{}).run();
        assertEquals(16,calls.get());JSONArray qs=result.getJSONArray("questions");
        for(int i=0;i<40;i++)assertEquals(i+1,qs.getJSONObject(i).getInt("slot"));
    }
    @Test public void requestRequiresSearchAndUsesStrongerModelWithoutTemperature() throws Exception {
        JSONObject j=journal(1);JSONObject b=new QuizGeneration(j,null,null,null).request(j.getJSONArray("plan"),null,new JSONArray(),"");
        assertEquals("gpt-5.6-sol",b.getString("model"));assertEquals("required",b.getString("tool_choice"));
        assertEquals("high",b.getJSONObject("reasoning").getString("effort"));assertTrue(b.getBoolean("background"));
        assertFalse(b.has("temperature"));assertEquals("web_search_call.action.sources",b.getJSONArray("include").getString(0));
    }
}
