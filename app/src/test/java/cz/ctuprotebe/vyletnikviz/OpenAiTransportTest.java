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
}
