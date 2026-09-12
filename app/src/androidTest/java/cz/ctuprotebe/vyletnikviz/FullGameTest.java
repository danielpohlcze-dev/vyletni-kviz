package cz.ctuprotebe.vyletnikviz;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.OutputStream;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.*;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FullGameTest {
    @Before public void reset(){
        Context c=ApplicationProvider.getApplicationContext();c.getSharedPreferences("quiz",0).edit().clear().commit();
    }
    @After public void release(){try{Intents.release();}catch(IllegalStateException ignored){}}

    static void game(MainActivity a,int count,int length){
        a.cfg=new MainActivity.Config();a.cfg.sound=false;a.cfg.title="Okoř";a.cfg.date="25. 6. 2026";
        a.players.clear();for(int i=0;i<count;i++)a.players.add(new MainActivity.Player("Hráč "+(i+1),"Historie",a.BLUE));
        a.loadAsset();a.trim(length);a.start();
    }
    static void noCrash(ThrowingAction action){try{action.run();}catch(Exception e){throw new AssertionError(e);}}
    interface ThrowingAction{void run()throws Exception;}

    @Test public void allFiveTakeoverValuesAndOwnerRotationWork(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{
                for(int count=2;count<=5;count++)for(int attempt=0;attempt<count;attempt++){
                    game(a,count,2);int correct=a.quiz.get(0).correct;
                    for(int n=0;n<attempt;n++)a.pass();
                    assertEquals(attempt,a.game.responder);assertEquals(Math.min(attempt,2),a.blocked());
                    assertFalse(a.game.blocked[correct]);assertFalse(a.game.answered);
                    a.answer(correct);assertEquals(GameRules.pointValue(attempt),a.game.scores[attempt],.0001);
                    assertEquals(1,a.game.correct[attempt]);assertEquals(attempt>0?1:0,a.game.steals[attempt]);
                    a.next();assertEquals(1,a.game.owner);assertEquals(1,a.game.responder);
                    assertEquals(0,a.game.attempt);assertEquals(0,a.blocked());
                }
            }));
        }
    }
    @Test public void wrongAnswerBlocksChoiceUndoRedoRestoresScoresAndState(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{game(a,3,2);int good=a.quiz.get(0).correct,bad=(good+1)%4;
                a.answer(bad);assertTrue(a.game.blocked[bad]);assertEquals(1,a.game.wrong[0]);
                a.answer(good);assertEquals(.6,a.game.scores[1],.0001);
                a.undo();assertFalse(a.game.answered);assertEquals(0,a.game.scores[1],.0001);assertEquals(1,a.game.responder);
                a.redo();assertTrue(a.game.answered);assertEquals(.6,a.game.scores[1],.0001);
                a.undo();a.pass();assertTrue(a.redo.isEmpty());assertEquals(2,a.game.responder);
            });
        }
    }
    @Test public void repeatedAnswersAndRepeatedFinishNeverDuplicatePointsOrHistory(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{game(a,2,1);int good=a.quiz.get(0).correct;
                a.next();assertFalse(a.gameFinished); // unresolved question cannot be skipped
                a.answer(good);a.answer(good);a.pass();assertEquals(1,a.game.scores[0],.0001);
                a.next();a.next();a.finishQuiz();
                assertEquals(1,new JSONArray(a.prefs.getString("history","[]")).length());
                assertFalse(a.prefs.contains("resume"));
            }));
        }
    }
    @Test public void savedGameSurvivesActivityRecreationAndKeepsCurrentResponder(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{game(a,5,4);a.answer(a.quiz.get(0).correct);a.next();a.pass();a.saveResume();});
            s.recreate();
            s.onActivity(a->{a.resume();assertEquals(5,a.players.size());assertEquals(1,a.game.index);
                assertEquals(1,a.game.owner);assertEquals(2,a.game.responder);assertEquals(1,a.blocked());
                assertEquals(1,a.game.scores[0],.0001);assertEquals("Okoř",a.cfg.title);
            });
        }
    }
    @Test public void fullFortyQuestionGameCreatesWinnerChronicleAndLeaderboard(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{game(a,5,40);a.players.get(0).name="Dan a spol.";
                for(int i=0;i<40;i++){assertEquals(i%5,a.game.owner);a.answer(a.quiz.get(i).correct);a.next();}
                JSONArray h=new JSONArray(a.prefs.getString("history","[]"));assertEquals(1,h.length());
                assertEquals(40,h.getJSONObject(0).getJSONArray("questions").length());
                for(int i=0;i<5;i++)assertEquals(8,a.game.scores[i],.0001);
                assertTrue(h.getJSONObject(0).getString("winner").contains("Dan a spol."));a.stats();
            }));
            onView(withText(containsString("Dan a spol.  •  1 výher"))).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    @Test public void importRejectsBrokenRecordAndDeduplicatesSharedTrip(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            final String[] export={""};
            s.onActivity(a->noCrash(()->{game(a,2,1);a.answer(a.quiz.get(0).correct);a.next();
                export[0]=TripHistory.PREFIX+new JSONArray(a.prefs.getString("history","[]")).getJSONObject(0);a.history();a.importTrip();
            }));
            onView(withHint("Vložte text začínající VYLETNI_KVIZ_2:")).inRoot(isDialog()).perform(replaceText(TripHistory.PREFIX+"{}"),closeSoftKeyboard());
            onView(withText("Importovat")).inRoot(isDialog()).perform(click());
            s.onActivity(a->noCrash(()->{assertEquals(1,new JSONArray(a.prefs.getString("history","[]")).length());a.importTrip();}));
            onView(withHint("Vložte text začínající VYLETNI_KVIZ_2:")).inRoot(isDialog()).perform(replaceText(export[0]),closeSoftKeyboard());
            onView(withText("Importovat")).inRoot(isDialog()).perform(click());
            s.onActivity(a->noCrash(()->{assertEquals(1,new JSONArray(a.prefs.getString("history","[]")).length());
                a.prefs.edit().remove("history").commit();a.importTrip();}));
            onView(withHint("Vložte text začínající VYLETNI_KVIZ_2:")).inRoot(isDialog()).perform(replaceText(export[0]),closeSoftKeyboard());
            onView(withText("Importovat")).inRoot(isDialog()).perform(click());
            s.onActivity(a->noCrash(()->assertEquals("Okoř",new JSONArray(a.prefs.getString("history","[]")).getJSONObject(0).getString("title"))));
        }
    }
    @Test public void sharingLaunchesChooserWithoutSendingAMessage(){
        Intents.init();intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null));
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{game(a,2,1);a.answer(a.quiz.get(0).correct);a.next();a.shareTrip(0);});
            intended(hasAction(Intent.ACTION_CHOOSER));
            Intent chooser=Intents.getIntents().stream().filter(i->Intent.ACTION_CHOOSER.equals(i.getAction())).findFirst().get();
            Intent payload=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals(Intent.ACTION_SEND,payload.getAction());assertEquals("text/plain",payload.getType());
            assertTrue(payload.getStringExtra(Intent.EXTRA_TEXT).startsWith(TripHistory.PREFIX));
            assertFalse(payload.getStringExtra(Intent.EXTRA_TEXT).contains("openai_secret"));
        }
    }
    @Test public void actualImageUriRendersAndSurvivesChronicleStorage(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{
                ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"quiz-test.png");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                Uri uri=a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                assertNotNull(uri);
                try{
                    Bitmap bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN);
                    try(OutputStream out=a.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
                    bitmap.recycle();a.setup();
                    a.onActivityResult(MainActivity.PICK_PHOTOS,Activity.RESULT_OK,new Intent().setData(uri));
                    assertEquals(1,a.selectedPhotos.size());assertEquals(1,a.photoHolder.getChildCount());
                    assertNotNull(((android.widget.ImageView)a.photoHolder.getChildAt(0)).getDrawable());
                    a.onActivityResult(MainActivity.PICK_PHOTOS,Activity.RESULT_OK,new Intent().setData(uri));assertEquals(1,a.selectedPhotos.size());
                    game(a,2,1);a.cfg.photos.add(uri.toString());a.answer(a.quiz.get(0).correct);a.next();
                    assertEquals(uri.toString(),new JSONArray(a.prefs.getString("history","[]")).getJSONObject(0).getJSONArray("photos").getString(0));
                    a.historyDetail(0);
                }finally{a.getContentResolver().delete(uri,null,null);}
            }));
        }
    }
    @Test public void localTestCredentialIsEncryptedAndCanBeRemoved(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{
                String fixture="sk-fake-test-only-not-an-api-key";a.setSecret(fixture);
                assertEquals(fixture,a.getSecret());assertNotEquals(fixture,a.prefs.getString("openai_secret",""));
                a.clearSecret();assertEquals("",a.getSecret());assertFalse(a.prefs.contains("openai_secret"));
            }));
        }
    }
    @Test public void generationContextPreservesPhotosModesAndNames(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->noCrash(()->{game(a,5,2);a.cfg.count=30;a.cfg.diff="Náročná";
                a.cfg.cats.add("Sport");a.cfg.photos.add("content://fixture/photo");a.cfg.strongEvery=4;a.cfg.bizarreEvery=8;
                JSONObject j=a.generationContext();a.cfg=null;a.players.clear();a.restoreGenerationContext(new JSONObject(j.toString()));
                assertEquals(30,a.cfg.count);assertEquals(5,a.players.size());assertEquals("Náročná",a.cfg.diff);
                assertEquals(4,a.cfg.strongEvery);assertEquals(8,a.cfg.bizarreEvery);assertEquals(1,a.cfg.photos.size());
                assertEquals(30,a.generationPlan().length());
            }));
        }
    }
}
