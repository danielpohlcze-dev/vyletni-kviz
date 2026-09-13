package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import org.junit.Test;
import java.net.*;
import java.util.*;
import static org.junit.Assert.*;

public class OpenAiTransportTest {
    static JSONObject body() throws Exception {
        JSONObject j=QuizGenerationTest.journal(1);
        return new QuizGeneration(j,null,null,null).request(j.getJSONArray("plan"),null,new JSONArray(),"");
    }
    @Test public void pollingNetworkFailureDoesNotRepeatPostAfterRestart() throws Exception {
        JSONObject j=QuizGenerationTest.journal(1);List<String> disk=new ArrayList<>();List<String> calls=new ArrayList<>();
        OpenAiTransport first=new OpenAiTransport("fake-test-key",j,()->{disk.clear();disk.add(j.toString());}){
            @Override JSONObject http(String method,String url,JSONObject body)throws Exception{
                calls.add(method);if(method.equals("GET"))throw new UnknownHostException();
                return new JSONObject().put("id","resp_saved").put("status","queued");
            }
            @Override void waitForPoll(long millis){}
        };
        assertThrows(UnknownHostException.class,()->first.call(body()));
        JSONObject restored=new JSONObject(disk.get(0));
        OpenAiTransport second=new OpenAiTransport("fake-test-key",restored,()->{}){
            @Override JSONObject http(String method,String url,JSONObject body)throws Exception{
                calls.add(method);assertEquals("GET",method);assertTrue(url.contains("resp_saved"));
                return QuizGenerationTest.response("questions",new JSONArray().put(QuizGenerationTest.question(1)));
            }
        };
        second.call(body());assertEquals(Arrays.asList("POST","GET","GET"),calls);
        second.call(body());assertEquals(3,calls.size()); // persisted completed result is reused too
    }
    @Test public void ambiguousPostTimeoutIsNeverAutomaticallyReplayed() throws Exception {
        final int[] calls={0};OpenAiTransport t=new OpenAiTransport("fake-test-key",new JSONObject(),()->{}){
            @Override JSONObject http(String m,String u,JSONObject b)throws Exception{calls[0]++;throw new SocketTimeoutException();}
        };
        assertThrows(SocketTimeoutException.class,()->t.call(body()));assertEquals(1,calls[0]);
    }
    @Test public void dnsFailureGetsTwoBoundedRetriesAndCorrectMessage() throws Exception {
        final int[] calls={0};OpenAiTransport t=new OpenAiTransport("fake-test-key",new JSONObject(),()->{}){
            @Override JSONObject http(String m,String u,JSONObject b)throws Exception{calls[0]++;throw new UnknownHostException();}
            @Override void waitForPoll(long millis){}
        };
        assertThrows(UnknownHostException.class,()->t.call(body()));assertEquals(3,calls[0]);
        assertEquals("Nepodařilo se připojit",OpenAiTransport.errorTitle(new UnknownHostException()));
        assertFalse(OpenAiTransport.errorMessage(new UnknownHostException()).contains("nespolehlivý"));
    }
    @Test public void authFailureIsNotRetriedOrTreatedAsBadQuiz() throws Exception {
        final int[] calls={0};OpenAiTransport t=new OpenAiTransport("fake-test-key",new JSONObject(),()->{}){
            @Override JSONObject http(String m,String u,JSONObject b)throws Exception{calls[0]++;throw new ApiException(401,statusMessage(401));}
        };
        assertThrows(OpenAiTransport.ApiException.class,()->t.call(body()));assertEquals(1,calls[0]);
    }
    @Test public void pausePreventsAnyFurtherRequests() throws Exception {
        OpenAiTransport t=new OpenAiTransport("fake-test-key",new JSONObject(),()->{}){
            @Override JSONObject http(String m,String u,JSONObject b){fail("Paused job must not contact API");return null;}
        };t.pause();assertThrows(java.io.InterruptedIOException.class,()->t.call(body()));
    }
    @Test public void reportsActualServerProgressAndPreservesTerminalReasonForGenerator() throws Exception {
        List<String> progress=new ArrayList<>(),calls=new ArrayList<>();JSONObject journal=QuizGenerationTest.journal(1);
        OpenAiTransport t=new OpenAiTransport("fake-test-key",journal,()->{},progress::add){
            @Override JSONObject http(String method,String url,JSONObject body)throws Exception{
                calls.add(method);
                if(calls.size()==1)return new JSONObject().put("id","resp_saved").put("status","queued");
                if(calls.size()==2)return new JSONObject().put("id","resp_saved").put("status","in_progress");
                return new JSONObject().put("id","resp_saved").put("status","incomplete")
                        .put("incomplete_details",new JSONObject().put("reason","max_output_tokens"));
            }
            @Override void waitForPoll(long millis){}
        };
        JSONObject raw=t.call(body());
        assertEquals(Arrays.asList("POST","GET","GET"),calls);
        assertTrue(progress.stream().anyMatch(s->s.startsWith("Čekám na uvolnění AI")));
        assertTrue(progress.stream().anyMatch(s->s.startsWith("AI právě pracuje")));
        QuizGeneration.ResponseException e=assertThrows(QuizGeneration.ResponseException.class,()->QuizGeneration.decode(raw));
        assertEquals("AI požadavek nedokončila",OpenAiTransport.errorTitle(e));
        assertTrue(e.outputLimit());assertFalse(OpenAiTransport.errorMessage(e).contains("kredit"));
    }
}
