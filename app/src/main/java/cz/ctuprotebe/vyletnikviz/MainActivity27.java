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
                    .setTitle("Máte rozpracovaný výletní kvíz")
                    .setMessage("Můžete navázat přesně tam, kde příprava skončila. Stejné zadání se nebude zbytečně generovat znovu.")
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
        title("CHYSTÁM VÝLETNÍ KVÍZ", cfg.title + " • " + cfg.count + " otázek");
        TextView progress = tv("Míchám otázky pro vaši partu…", 20, true);
        root.addView(progress);
        root.addView(tv("Česko, svět, silné okruhy hráčů a občas pravdivá bizarnost. Každou otázku potom nezávisle zkontroluji, aby měla jedinou obhajitelnou odpověď.", 16, false));
        root.addView(tv("Když kontrola najde spornou otázku, opraví se jen ona. Server má nejvýše tři cílená opravná kola, takže příprava nemůže běžet donekonečna.", 15, false));
        root.addView(tv("Může to chvíli trvat, ale zadání je bezpečně uložené. Až bude kvíz připravený, samotné hraní funguje i bez internetu.", 15, false));
        Button pause = secondary("Uložit a pokračovat později");
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
                            + "\n\nServer už vyčerpal omezená cílená opravná kola. Stejné ID znovu nespouštím, aby nevznikala nekonečná smyčka.")
                    .setPositiveButton("Vytvořit nový pokus", (d, w) -> {
                        prefs.edit().remove("ai_pending").apply();
                        newGeneration();
                    })
                    .setNegativeButton("Později", null);
        } else {
            dialog.setMessage(ServerQuizClient.errorMessage(e)
                            + "\n\nZadání zůstalo uložené. Pokračování použije stejné ID přípravy a naváže na uložený stav.")
                    .setPositiveButton("Pokračovat v přípravě", (d, w) -> resumeGeneration())
                    .setNegativeButton("Později", null);
        }
        dialog.show();
    }

    @Override void connection() {
        base();
        title("PŘIPOJENÍ K AI", "Tvorba i kontrola kvízu probíhá na serveru.");
        TextView ok = tv("✓ V telefonu není potřeba OpenAI API klíč", 18, true);
        ok.setTextColor(GREEN);
        root.addView(ok);
        root.addView(tv("Telefon odešle jen plán hry. Server vytvoří otázky a nezávislý kontrolní krok je znovu vyřeší bez znalosti autorovy označené odpovědi.", 15, false));
        root.addView(tv("Sporné otázky se nevydají do hry. Opravují se jen problematické kusy, nejvýše ve třech cílených kolech.", 15, false));
        root.addView(tv("Při výpadku připojení se používá stejné ID přípravy, takže lze později bezpečně pokračovat.", 15, false));

        if (!getSecret().isEmpty()) {
            TextView legacy = tv("Ve starší verzi zůstal v telefonu zašifrovaný OpenAI klíč. Tato verze ho nepoužívá.", 14, false);
            legacy.setTextColor(Color.rgb(120, 80, 20));
            root.addView(legacy);
            Button remove = secondary("Odstranit starý OpenAI klíč z telefonu");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Odstranit starý klíč?")
                    .setMessage("Pro tuto verzi už není potřeba. Historie ani kvízy se nesmažou.")
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
