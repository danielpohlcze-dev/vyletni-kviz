package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Saves the response ID before polling, so a lost connection does not submit a second paid job. */
class OpenAiTransport implements QuizGeneration.Transport {
    static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private final String apiKey;
    private final JSONObject journal;
    private final QuizGeneration.Checkpoint checkpoint;
    volatile boolean paused;
    private volatile HttpURLConnection active;

    static final class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    OpenAiTransport(String apiKey, JSONObject journal, QuizGeneration.Checkpoint checkpoint) {
        this.apiKey = apiKey; this.journal = journal; this.checkpoint = checkpoint;
    }

    void pause() {
        paused = true;
        HttpURLConnection c = active;
        if (c != null) c.disconnect();
    }

    @Override public JSONObject call(JSONObject body) throws Exception {
        checkPaused();
        JSONObject cached = journal.optJSONObject("pending_result");
        if (cached != null) return cached;
        String phase = body.getJSONObject("text").getJSONObject("format").getString("name");
        JSONObject pending = journal.optJSONObject("pending_response");
        JSONObject response;
        if (pending == null) {
            // Retry only failures before any server connection; an ambiguous POST timeout is not replayed.
            response = null;
            for (int attempt = 0; response == null; attempt++) {
                try { response = http("POST", ENDPOINT, body); }
                catch (UnknownHostException | ConnectException e) {
                    if (attempt >= 2) throw e;
                    waitForPoll(1000L * (attempt + 1));
                }
            }
            String id = response.optString("id");
            if (!id.matches("resp_[A-Za-z0-9_-]+")) throw new IOException("Server neposkytl identifikátor přípravy. Zkuste to znovu.");
            pending = new JSONObject().put("id", id).put("phase", phase);
            journal.put("pending_response", pending);
            checkpoint.save();
        } else {
            if (!phase.equals(pending.getString("phase"))) throw new IOException("Uložená fáze přípravy neodpovídá zadání.");
            response = retrieve(pending.getString("id"));
        }
        int polls = 0;
        while ("queued".equals(response.optString("status")) || "in_progress".equals(response.optString("status"))) {
            if (++polls > 450) throw new SocketTimeoutException("Příprava stále běží. Můžete se k ní později vrátit.");
            waitForPoll(2000);
            response = retrieve(pending.getString("id"));
        }
        checkPaused();
        // The engine consumes result + ID in the same checkpoint as draft / accepted questions.
        journal.put("pending_result", response);
        checkpoint.save();
        return response;
    }

    private JSONObject retrieve(String id) throws Exception {
        if (!id.matches("resp_[A-Za-z0-9_-]+")) throw new IOException("Neplatný identifikátor uložené přípravy.");
        try {
            return http("GET", ENDPOINT + "/" + id + "?include%5B%5D=web_search_call.action.sources", null);
        } catch (ApiException e) {
            if (e.status == 404) {
                journal.remove("pending_response"); checkpoint.save();
                throw new IOException("Server už tuto rozpracovanou odpověď nemá. Hotové otázky zůstaly uložené; pokračování zopakuje jen chybějící část.");
            }
            throw e;
        }
    }

    void waitForPoll(long millis) throws InterruptedException, InterruptedIOException {
        checkPaused(); Thread.sleep(millis); checkPaused();
    }
    private void checkPaused() throws InterruptedIOException {
        if (paused || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Příprava byla pozastavena.");
    }

    JSONObject http(String method, String url, JSONObject body) throws Exception {
        checkPaused();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        active = c;
        try {
            c.setConnectTimeout(20000); c.setReadTimeout(45000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            c.setRequestProperty("Content-Type", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new ApiException(code, statusMessage(code));
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    checkPaused();
                    if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Odpověď serveru je příliš velká.");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { c.disconnect(); active = null; }
    }

    static String statusMessage(int code) {
        if (code == 401) return "OpenAI odmítlo uložený API klíč. Ověřte jej v Připojení k AI.";
        if (code == 403 || code == 404) return "Projekt nemá přístup k požadovanému modelu nebo funkci (HTTP " + code + ").";
        if (code == 429) return "OpenAI hlásí limit požadavků nebo vyčerpaný kredit. Zkontrolujte kredit a zkuste pokračovat později.";
        if (code >= 500) return "OpenAI má dočasné potíže. Hotová část zůstává uložená; zkuste pokračovat později.";
        return "OpenAI nepřijalo požadavek (HTTP " + code + "). Hotová část zůstává uložená.";
    }
    static String errorTitle(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) return "Nepodařilo se připojit";
        if (e instanceof SocketTimeoutException) return "Čekání na server se přerušilo";
        if (e instanceof QuizGeneration.QualityException) return "Část otázek potřebuje další kontrolu";
        if (e instanceof ApiException) return "OpenAI požadavek nepřijalo";
        return "Příprava byla přerušena";
    }
    static String errorMessage(Exception e) {
        if (e instanceof UnknownHostException) return "Telefon nedokázal najít server OpenAI. Zkuste přepnout mezi Wi-Fi a mobilními daty. API klíč kvůli této chybě neměňte.";
        if (e instanceof ConnectException) return "Server není dostupný z tohoto připojení. Zkontrolujte internet a zkuste pokračovat.";
        if (e instanceof SocketTimeoutException) return "Server nestihl odpovědět. Pokračování nejdřív načte výsledek už odeslané úlohy, pokud je uložen její identifikátor.";
        if (e instanceof QuizGeneration.QualityException || e instanceof ApiException) return e.getMessage();
        return "Přípravu se nepodařilo dokončit. Hotové otázky i nastavení jsou uložené; můžete pokračovat později.";
    }
}
