package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Saves the response ID before polling, so a lost connection does not submit a second paid job. */
class OpenAiTransport implements QuizGeneration.Transport {
    static final String ENDPOINT = "https://api.openai.com/v1/responses";
    // A phone can briefly lose DNS while switching towers, Wi-Fi access points or power modes.
    // UnknownHost / ConnectException happen before an HTTP response, so retrying them is safe.
    // SocketTimeoutException is intentionally excluded because a POST may already be accepted.
    static final long[] CONNECTION_RETRY_DELAYS_MS = {2000, 3000, 5000, 8000, 13000, 21000};
    private final String apiKey;
    private final JSONObject journal;
    private final QuizGeneration.Checkpoint checkpoint;
    private final QuizGeneration.Progress progress;
    volatile boolean paused;
    private volatile HttpURLConnection active;

    static final class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    OpenAiTransport(String apiKey, JSONObject journal, QuizGeneration.Checkpoint checkpoint) {
        this(apiKey, journal, checkpoint, text -> {});
    }

    OpenAiTransport(String apiKey, JSONObject journal, QuizGeneration.Checkpoint checkpoint, QuizGeneration.Progress progress) {
        this.apiKey = apiKey; this.journal = journal; this.checkpoint = checkpoint;
        this.progress = progress;
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
            progress.show("Odesílám novou úlohu…");
            // Retry only failures before any server connection; an ambiguous POST timeout is not replayed.
            response = httpWithConnectionRetry("POST", ENDPOINT, body,
                    "Telefon hledá spojení s OpenAI");
            String id = response.optString("id");
            if (!id.matches("resp_[A-Za-z0-9_-]+")) throw new IOException("Server neposkytl identifikátor přípravy. Zkuste to znovu.");
            pending = new JSONObject().put("id", id).put("phase", phase).put("started_at", System.currentTimeMillis());
            journal.put("pending_response", pending);
            checkpoint.save();
        } else {
            if (!phase.equals(pending.getString("phase"))) throw new IOException("Uložená fáze přípravy neodpovídá zadání.");
            progress.show("Navazuji na již odeslanou úlohu…");
            response = retrieve(pending.getString("id"));
        }
        long started = pending.optLong("started_at", System.currentTimeMillis());
        int polls = 0;
        while ("queued".equals(response.optString("status")) || "in_progress".equals(response.optString("status"))) {
            long seconds = Math.max(0, (System.currentTimeMillis() - started) / 1000);
            progress.show(("queued".equals(response.optString("status")) ? "Čekám na uvolnění AI" : "AI právě pracuje")
                    + " • " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)
                    + " • Stav potvrzen serverem");
            if (++polls > 450) throw new SocketTimeoutException("Příprava stále běží. Můžete se k ní později vrátit.");
            waitForPoll(2000);
            response = retrieve(pending.getString("id"));
        }
        checkPaused();
        // The engine consumes result + ID in the same checkpoint as draft / accepted questions.
        journal.put("pending_result", response);
        checkpoint.save();
        progress.show("completed".equals(response.optString("status")) ? "Odpověď dorazila, zpracovávám ji…" : "Server úlohu ukončil, vyhodnocuji její stav…");
        return response;
    }

    private JSONObject retrieve(String id) throws Exception {
        if (!id.matches("resp_[A-Za-z0-9_-]+")) throw new IOException("Neplatný identifikátor uložené přípravy.");
        try {
            return httpWithConnectionRetry("GET",
                    ENDPOINT + "/" + id + "?include%5B%5D=web_search_call.action.sources", null,
                    "Spojení kolísá, bezpečně navazuji na uloženou úlohu");
        } catch (ApiException e) {
            if (e.status == 404) {
                journal.remove("pending_response"); checkpoint.save();
                throw new IOException("Server už tuto rozpracovanou odpověď nemá. Hotové otázky zůstaly uložené; pokračování zopakuje jen chybějící část.");
            }
            throw e;
        }
    }

    JSONObject httpWithConnectionRetry(String method, String url, JSONObject body, String retryProgress) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { return http(method, url, body); }
            catch (UnknownHostException | ConnectException e) {
                if (attempt >= CONNECTION_RETRY_DELAYS_MS.length) throw e;
                progress.show(retryProgress + " • pokus " + (attempt + 2) + "/" +
                        (CONNECTION_RETRY_DELAYS_MS.length + 1));
                waitForPoll(CONNECTION_RETRY_DELAYS_MS[attempt]);
            }
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
        if (e instanceof QuizGeneration.ResponseException) return "AI požadavek nedokončila";
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
