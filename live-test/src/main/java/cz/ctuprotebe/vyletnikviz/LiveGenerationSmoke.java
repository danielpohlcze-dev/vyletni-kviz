package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in paid smoke test of the SAME transport and generator compiled into the APK. */
public final class LiveGenerationSmoke {
    static final int MAX_POSTS = 4; // Two groups of five: author + reviewer. No unbounded repairs.
    static final class BudgetExceeded extends IOException { }
    static final class Counters {
        int posts, gets, inputTokens, outputTokens, searchCalls;
        boolean interruptionInjected;
        void beforePost() throws BudgetExceeded { if (posts >= MAX_POSTS) throw new BudgetExceeded(); posts++; }
        JSONObject json() throws JSONException {
            return new JSONObject().put("post_attempts",posts).put("get_attempts",gets)
                    .put("input_tokens",inputTokens).put("output_tokens",outputTokens)
                    .put("web_tool_calls",searchCalls).put("simulated_poll_interruption",interruptionInjected);
        }
    }

    static JSONObject journal() throws JSONException {
        String[] names={"Barča","Dominik","Daneček"};
        String[] topics={"Česko: historie", "Svět: příroda", "Věda a technika",
                "Český rap", "Slovenské politické události v letech 2020–2024",
                "Anglická složitější slova v kontextu a jejich význam v češtině",
                "Bizarní svět: doložené překvapivé přírodní jevy", "Česko: zeměpis",
                "Historie vědy", "Česká kultura"};
        JSONArray plan=new JSONArray();
        for(int i=0;i<10;i++)plan.put(new JSONObject().put("slot",i+1).put("assigned_player",names[i%3])
                .put("requested_topic",topics[i]).put("hard",i%3==2));
        return new JSONObject().put("version",QuizGeneration.VERSION).put("as_of",LocalDate.now().toString())
                .put("plan",plan).put("accepted",new JSONArray());
    }

    static JSONObject safeFailure(Exception e) throws JSONException {
        JSONObject result=new JSONObject().put("passed",false).put("failure_type",e.getClass().getSimpleName());
        if(e instanceof OpenAiTransport.ApiException)result.put("http_status",((OpenAiTransport.ApiException)e).status);
        // Deliberately omit arbitrary error messages, stack traces, request bodies and credentials.
        return result;
    }

    static JSONObject restore(Path path) throws Exception {
        JSONObject fresh=journal();
        if(!Files.exists(path))return fresh;
        if(Files.size(path)>4*1024*1024)throw new IOException("Invalid checkpoint size");
        JSONObject saved=new JSONObject(Files.readString(path));
        if(saved.getInt("version")!=QuizGeneration.VERSION||!saved.getJSONArray("plan").similar(fresh.getJSONArray("plan")))
            throw new IOException("Checkpoint does not match the test fixture");
        if(saved.getJSONArray("accepted").length()>10)throw new IOException("Invalid checkpoint");
        return saved;
    }

    static void saveCheckpoint(Path output,JSONObject state)throws Exception {
        // This fixture journal contains questions, web results and response IDs, never credentials.
        Path temp=output.resolve("resume.tmp");Files.writeString(temp,state.toString(2));
        Files.move(temp,output.resolve("resume.json"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("accepted-questions.json"),new JSONObject().put("complete",false)
                .put("questions",state.getJSONArray("accepted")).toString(2));
    }

    public static void main(String[] args) throws Exception {
        String key=System.getenv("OPENAI_API_KEY");
        if(key==null||key.trim().isEmpty()) {
            System.err.println("Chybí repository secret OPENAI_API_KEY. Nebyl odeslán žádný placený požadavek.");
            System.exit(2);return;
        }
        Path output=Paths.get("build/live-result");Files.createDirectories(output);
        JSONObject journal=restore(Paths.get("resume.json"));Counters counts=new Counters();long start=System.currentTimeMillis();
        // A serialized checkpoint is used to simulate process loss without exposing the journal as an artifact.
        final String[] checkpoint={journal.toString()};AtomicInteger resumes=new AtomicInteger();
        JSONObject report=null;boolean passed=false;
        try {
            while(true) {
                final JSONObject state=journal;
                QuizGeneration.Checkpoint save=()->{checkpoint[0]=state.toString();saveCheckpoint(output,state);};
                OpenAiTransport transport=new OpenAiTransport(key,state,save) {
                    @Override JSONObject http(String method,String url,JSONObject body)throws Exception {
                        if("POST".equals(method)) {
                            counts.beforePost();
                            body.put("max_tool_calls",8); // Additional smoke-test spending bound.
                        } else {
                            counts.gets++;
                            if(!counts.interruptionInjected) {
                                counts.interruptionInjected=true;
                                throw new SocketTimeoutException("Simulated polling interruption");
                            }
                        }
                        return super.http(method,url,body);
                    }
                    @Override public JSONObject call(JSONObject body)throws Exception {
                        JSONObject raw=super.call(body);
                        JSONObject usage=raw.optJSONObject("usage");
                        if(usage!=null){counts.inputTokens+=usage.optInt("input_tokens");counts.outputTokens+=usage.optInt("output_tokens");}
                        JSONArray items=raw.optJSONArray("output");
                        for(int i=0;items!=null&&i<items.length();i++)
                            if("web_search_call".equals(items.getJSONObject(i).optString("type")))counts.searchCalls++;
                        return raw;
                    }
                };
                try {
                    JSONObject quiz=new QuizGeneration(state,transport,save,System.out::println).run();
                    if(quiz.getJSONArray("questions").length()!=10)throw new IOException();
                    Files.writeString(output.resolve("quiz.json"),quiz.toString(2));
                    report=new JSONObject().put("passed",true).put("questions",10);passed=true;break;
                } catch(SocketTimeoutException e) {
                    if(resumes.getAndIncrement()>=1||!counts.interruptionInjected)throw e;
                    journal=new JSONObject(checkpoint[0]);
                    if(!journal.has("pending_response"))throw e;
                    System.out.println("Obnovuji přerušené načítání podle uloženého ID; znovu jej neodesílám.");
                }
            }
        } catch(Exception e) { report=safeFailure(e); }
        finally {
            saveCheckpoint(output,journal);
            if(report==null)report=new JSONObject().put("passed",false).put("failure_type","Interrupted");
            report.put("model",QuizGeneration.MODEL).put("counts",counts.json())
                    .put("accepted_questions",journal.getJSONArray("accepted").length())
                    .put("quality_feedback",journal.optString("feedback"))
                    .put("elapsed_seconds",(System.currentTimeMillis()-start)/1000)
                    .put("live_recovery_exercised",counts.interruptionInjected&&resumes.get()>0)
                    .put("limit_note","Max 4 POST attempts, 18000 output tokens and 8 web-tool calls per request. Token limits are not a fixed dollar budget.");
            Files.writeString(output.resolve("summary.json"),report.toString(2));
        }
        System.out.println(passed?"Živý test vytvořil a zkontroloval 10 otázek.":"Živý test neprošel; viz sanitized summary.json.");
        if(!passed)System.exit(1);
    }
}
