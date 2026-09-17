package cz.ctuprotebe.vyletnikviz;

import android.app.AlertDialog;
import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Version 2.7 keeps the complete multiplayer UI/game engine from MainActivity,
 * but moves generation and factual verification to the server.
 */
public class MainActivity27 extends MainActivity {
    ServerQuizClient serverClient;

    @Override void generate(Button b) {
        if (prefs.contains("ai_pending")) {
            new AlertDialog.Builder(this)
                    .setTitle("Máte rozpracovanou přípravu")
                    .setMessage("Můžete pokračovat v uloženém zadání. Verze 2.7 už nepoužívá staré placené dávky z telefonu.")
                    .setPositiveButton("Pokračovat v uloženém", (d, w) -> resumeGeneration())
                    .setNegativeButton("Nahradit novým", (d, w) -> newGeneration())
                    .show();
        } else newGeneration();
    }

    @Override void newGeneration() {
        try {
            JSONObject journal = new JSONObject()
                    .put("version", 27)
                    .put("request_id", newRequestId())
                    .put("context", generationContext())
                    .put("as_of", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()))
                    .put("plan", generationPlan());
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException();
            runGeneration(journal);
        } catch (Exception e) {
            toast("Přípravu se nepodařilo uložit. Zkuste uvolnit místo v telefonu.");
        }
    }

    @Override void resumeGeneration() {
        try {
            JSONObject journal = new JSONObject(prefs.getString("ai_pending", ""));
            if (journal.optInt("version") != 27) {
                if (!journal.has("context") || !journal.has("plan")) throw new JSONException("missing context");
                journal.put("version", 27).put("request_id", newRequestId());
                String[] old = {"pending_response", "pending_result", "draft", "accepted", "feedback", "events", "batch_size", "server_result"};
                for (String key : old) journal.remove(key);
                if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException();
            }
            restoreGenerationContext(journal.getJSONObject("context"));
            runGeneration(journal);
        } catch (Exception e) {
            toast("Rozpracovanou přípravu se nepodařilo načíst. Můžete ji nahradit novým zadáním.");
        }
    }

    @Override void runGeneration(JSONObject journal) {
        if (!AI_BUSY.compareAndSet(false, true)) {
            toast("Předchozí příprava ještě běží. Zkuste to za chvíli.");
            return;
        }
        generationRunning = true;
        base();
        title("PŘIPRAVUJI KVÍZ", cfg.title + " • " + cfg.count + " otázek");
        TextView progress = tv("Připravuji otázky a ověřuji odpovědi…", 20, true);
        root.addView(progress);
        root.addView(tv("Server nejdřív vytvoří celý kvíz a potom druhý AI krok každou otázku nezávisle vyřeší bez znalosti autorovy označené odpovědi.", 16, false));
        root.addView(tv("Když kontrola některou otázku odmítne, server nahradí nejvýše jednou pouze problematické otázky a znovu ověří jen tyto náhrady. Nevzniká nekonečná opravná smyčka.", 15, false));
        root.addView(tv("Při výpadku sítě se používá stejné ID přípravy. Server si stav tohoto ID pamatuje, takže opakování nenastartuje novou přípravu.", 15, false));
        Button pause = secondary("Pozastavit a uložit");
        root.addView(pause);

        ServerQuizClient.Checkpoint save = () -> {
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException("Přípravu nelze uložit.");
        };
        serverClient = new ServerQuizClient(journal, save,
                text -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(text); }));

        pause.setOnClickListener(v -> {
            pause.setEnabled(false);
            pause.setText("Ukládám přípravu…");
            serverClient.pause();
            if (generationThread != null) generationThread.interrupt();
        });

        generationThread = new Thread(() -> {
            try {
                JSONObject complete = serverClient.generate();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    try {
                        parse(complete);
                        cfg.personalized = true;
                        prefs.edit().remove("ai_pending").apply();
                        start();
                    } catch (Exception e) {
                        showServerError(e);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    if (serverClient != null && serverClient.paused) {
                        home();
                        toast("Příprava je uložená. Můžete pokračovat později.");
                    } else showServerError(e);
                });
            } finally {
                AI_BUSY.set(false);
            }
        }, "quiz-server-generation");
        generationThread.start();
    }

    void showServerError(Exception e) {
        home();
        boolean terminalQualityFailure = e instanceof ServerQuizClient.ServerException
                && ((ServerQuizClient.ServerException) e).status == 502;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(ServerQuizClient.errorTitle(e));
        if (terminalQualityFailure) {
            dialog.setMessage(ServerQuizClient.errorMessage(e)
                            + "\n\nServer už provedl maximálně jednu cílenou opravu problematických otázek. Stejné ID se znovu negeneruje, aby se zbytečně nepálily kredity.")
                    .setPositiveButton("Vytvořit nový pokus", (d, w) -> {
                        prefs.edit().remove("ai_pending").apply();
                        newGeneration();
                    })
                    .setNegativeButton("Později", null);
        } else {
            dialog.setMessage(ServerQuizClient.errorMessage(e)
                            + "\n\nZadání zůstalo uložené. Pokračování použije stejné ID přípravy a nevytvoří další placenou úlohu.")
                    .setPositiveButton("Pokračovat v přípravě", (d, w) -> resumeGeneration())
                    .setNegativeButton("Později", null);
        }
        dialog.show();
    }

    @Override void connection() {
        base();
        title("PŘIPOJENÍ K AI", "Od verze 2.7 probíhá tvorba i kontrola kvízu na našem serveru.");
        TextView ok = tv("✓ Telefon už nevolá api.openai.com", 18, true);
        ok.setTextColor(GREEN);
        root.addView(ok);
        root.addView(tv("Nový kvíz odešle pouze plán hry. Server nejdřív vytvoří otázky a potom je druhý AI krok nezávisle vyřeší bez znalosti označených odpovědí autora.", 15, false));
        root.addView(tv("Pokud některá otázka neprojde, server nahradí nejvýše jednou jen odmítnuté otázky. Kvíz vydá pouze tehdy, když kontrola potvrdí faktickou správnost, jednoznačnost a vysokou jistotu.", 15, false));
        root.addView(tv("V telefonu proto není potřeba nastavovat ani měnit OpenAI API klíč.", 15, false));

        if (!getSecret().isEmpty()) {
            TextView legacy = tv("Ve starší verzi zůstal v telefonu zašifrovaný OpenAI klíč. Verze 2.7 ho nepoužívá.", 14, false);
            legacy.setTextColor(Color.rgb(120, 80, 20));
            root.addView(legacy);
            Button remove = secondary("Odstranit starý OpenAI klíč z telefonu");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Odstranit starý klíč?")
                    .setMessage("Pro verzi 2.7 už není potřeba. Historie ani kvízy se nesmažou.")
                    .setPositiveButton("Odstranit", (d, w) -> { clearSecret(); toast("Starý klíč byl odstraněn"); connection(); })
                    .setNegativeButton("Ponechat", null)
                    .show());
            root.addView(remove);
        }
        Button back = secondary("Zpět");
        back.setOnClickListener(v -> home());
        root.addView(back);
    }

    @Override public void onBackPressed() {
        if (generationRunning) {
            if (serverClient != null) serverClient.pause();
            if (generationThread != null) generationThread.interrupt();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (serverClient != null) serverClient.pause();
        super.onDestroy();
    }

    static String newRequestId() {
        return "vk27_" + UUID.randomUUID().toString().replace("-", "");
    }
}
