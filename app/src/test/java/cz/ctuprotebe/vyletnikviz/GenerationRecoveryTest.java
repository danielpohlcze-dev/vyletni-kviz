package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import org.junit.Test;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static cz.ctuprotebe.vyletnikviz.QuizGenerationTest.*;

public class GenerationRecoveryTest {
    static JSONObject payload(JSONObject body) throws Exception {
        return new JSONObject(body.getJSONArray("input").getJSONObject(1).getString("content"));
    }
    static JSONObject good(JSONObject body) throws Exception {
        JSONArray slots = payload(body).getJSONArray("slots"), out = new JSONArray();
        for (int i=0;i<slots.length();i++) {
            int n=slots.getJSONObject(i).getInt("slot");
            out.put(isReview(body) ? review(n,true) : question(n));
        }
        return response(isReview(body)?"reviews":"questions",out);
    }
    static JSONObject limited() throws Exception {
        return new JSONObject().put("status","incomplete").put("incomplete_details",
                new JSONObject().put("reason","max_output_tokens"));
    }
    @Test public void thirtyQuestionsRecoverFromOutputLimitWithoutChangingModelOrTokenCap() throws Exception {
        JSONObject j=journal(30);AtomicInteger calls=new AtomicInteger();List<Integer> sizes=new ArrayList<>();
        JSONObject done=new QuizGeneration(j,b->{
            assertEquals("gpt-5.6-sol",b.getString("model"));
            assertEquals("high",b.getJSONObject("reasoning").getString("effort"));
            assertEquals(18000,b.getInt("max_output_tokens"));
            sizes.add(payload(b).getJSONArray("slots").length());
            if(calls.incrementAndGet()==1)return limited();
            return good(b);
        },()->{},t->{}).run();
        assertEquals(30,done.getJSONArray("questions").length());
        assertEquals(31,calls.get());assertEquals(Integer.valueOf(5),sizes.get(0));
        assertTrue(sizes.subList(1,sizes.size()).stream().allMatch(n->n<=2));
        for(int i=0;i<30;i++)assertEquals(i+1,done.getJSONArray("questions").getJSONObject(i).getInt("slot"));
    }
    @Test public void limitedReviewSplitsSavedDraftWithoutGeneratingItAgain() throws Exception {
        JSONObject j=journal(5);AtomicInteger authors=new AtomicInteger(),reviews=new AtomicInteger();
        JSONObject done=new QuizGeneration(j,b->{
            if(!isReview(b)){authors.incrementAndGet();return good(b);}
            if(reviews.incrementAndGet()==1)return limited();
            assertTrue(payload(b).getJSONObject("draft").getJSONArray("questions").length()<=2);
            return good(b);
        },()->{},t->{}).run();
        assertEquals(1,authors.get());assertEquals(4,reviews.get());
        assertEquals(5,done.getJSONArray("questions").length());assertFalse(j.has("draft"));
    }
    @Test public void repeatedTokenLimitStopsAtOneQuestionAndNeverAcceptsPartialJson() throws Exception {
        JSONObject j=journal(5);AtomicInteger calls=new AtomicInteger();List<Integer> sizes=new ArrayList<>();
        QuizGeneration.ResponseException e=assertThrows(QuizGeneration.ResponseException.class,()->new QuizGeneration(j,b->{
            calls.incrementAndGet();sizes.add(payload(b).getJSONArray("slots").length());
            return good(b).put("status","incomplete").put("incomplete_details",limited().getJSONObject("incomplete_details"));
        },()->{},t->{}).run());
        assertTrue(e.outputLimit());assertEquals(Arrays.asList(5,2,1),sizes);
        assertEquals(3,calls.get());assertEquals(0,j.getJSONArray("accepted").length());
    }
    @Test public void failedCancelledFilteredAndUnknownResponsesDoNotTriggerAutomaticPaidRetry() throws Exception {
        for(String state:Arrays.asList("failed","cancelled","incomplete")){
            JSONObject j=journal(1);AtomicInteger calls=new AtomicInteger();
            QuizGeneration.ResponseException e=assertThrows(QuizGeneration.ResponseException.class,()->new QuizGeneration(j,b->{
                calls.incrementAndGet();return new JSONObject().put("status",state)
                        .put("error",new JSONObject().put("message","PRIVATE_UPSTREAM_DETAIL"));
            },()->{},t->{}).run());
            assertEquals(1,calls.get());assertFalse(e.getMessage().contains("PRIVATE_UPSTREAM_DETAIL"));
            assertFalse(j.toString().contains("PRIVATE_UPSTREAM_DETAIL"));
        }
        AtomicInteger calls=new AtomicInteger();
        assertThrows(QuizGeneration.ResponseException.class,()->new QuizGeneration(journal(5),b->{
            calls.incrementAndGet();return limited().put("incomplete_details",new JSONObject().put("reason","content_filter"));
        },()->{},t->{}).run());assertEquals(1,calls.get());
    }
    @Test public void malformedReviewRetriesReviewOnlyAndKeepsAuthoredQuestions() throws Exception {
        JSONObject j=journal(2);AtomicInteger authors=new AtomicInteger(),reviews=new AtomicInteger();
        JSONObject done=new QuizGeneration(j,b->{
            if(!isReview(b)){authors.incrementAndGet();return good(b);}
            if(reviews.incrementAndGet()==1)return response("reviews",new JSONArray());
            return good(b);
        },()->{},t->{}).run();
        assertEquals(1,authors.get());assertEquals(2,reviews.get());
        assertEquals(2,done.getJSONArray("questions").length());
    }
    @Test public void reducedBatchSurvivesProcessRestart() throws Exception {
        JSONObject j=journal(5);List<String> disk=new ArrayList<>();AtomicInteger first=new AtomicInteger();
        assertThrows(UnknownHostException.class,()->new QuizGeneration(j,b->{
            if(first.incrementAndGet()==1)return limited();throw new UnknownHostException();
        },()->{disk.clear();disk.add(j.toString());},t->{}).run());
        JSONObject restored=new JSONObject(disk.get(0));assertEquals(2,restored.getInt("batch_size"));
        JSONObject done=new QuizGeneration(restored,b->{assertTrue(payload(b).getJSONArray("slots").length()<=2);return good(b);},()->{},t->{}).run();
        assertEquals(5,done.getJSONArray("questions").length());
    }
    @Test public void legacyStoppedPreparationStartsWithSmallerGroup() throws Exception {
        JSONObject j=journal(5).put("feedback","AI nedokončila odpověď. Neúplný kvíz nelze spustit.");
        new QuizGeneration(j,b->{assertTrue(payload(b).getJSONArray("slots").length()<=2);return good(b);},()->{},t->{}).run();
        assertEquals(2,j.getInt("batch_size"));
    }
    @Test public void existingPendingReviewKeepsItsOriginalShapeUntilItsResultIsRead() throws Exception {
        JSONObject j=journal(5).put("feedback","AI nedokončila odpověď. Neúplný kvíz nelze spustit.")
                .put("pending_response",new JSONObject().put("id","resp_fixture").put("phase","quiz_review"));
        JSONArray draft=new JSONArray();for(int i=1;i<=5;i++)draft.put(question(i));
        j.put("draft",new JSONObject().put("questions",draft));
        new QuizGeneration(j,b->{assertTrue(isReview(b));assertEquals(5,payload(b).getJSONArray("slots").length());return good(b);},()->{},t->{}).run();
        assertEquals(5,j.getJSONArray("accepted").length());
    }
    @Test public void invalidReviewCannotPartiallyMutateSavedDraftOrAcceptQuestions() throws Exception {
        JSONObject j=journal(2);AtomicInteger reviews=new AtomicInteger();
        assertThrows(UnknownHostException.class,()->new QuizGeneration(j,b->{
            if(!isReview(b))return good(b);
            if(reviews.incrementAndGet()>1)throw new UnknownHostException();
            return response("reviews",new JSONArray().put(review(1,true)).put(review(1,true)));
        },()->{},t->{}).run());
        assertEquals(0,j.getJSONArray("accepted").length());
        assertEquals(question(1).toString(),j.getJSONObject("draft").getJSONArray("questions").getJSONObject(0).toString());
    }
    @Test public void rejectionEvidenceSurvivesLaterSuccessfulReplacement() throws Exception {
        JSONObject j=journal(1);AtomicInteger reviews=new AtomicInteger();
        new QuizGeneration(j,b->{
            if(isReview(b)&&reviews.incrementAndGet()==1)return response("reviews",new JSONArray().put(review(1,false)));
            return good(b);
        },()->{},t->{}).run();
        JSONArray events=j.getJSONArray("events");boolean rejected=false;
        for(int i=0;i<events.length();i++){
            JSONObject event=events.getJSONObject(i);
            if("decision".equals(event.getString("kind"))&&"rejected".equals(event.getString("status")))
                rejected=event.getString("reason").contains("českým rapem");
        }
        assertTrue(rejected);assertEquals(1,j.getJSONArray("accepted").length());
    }
}
