package cz.ctuprotebe.vyletnikviz;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    static final String DEFAULT_ENDPOINT="https://vyletni-kviz-api.daniel-pohl.chatgpt.site/generate";
    final int NAVY=Color.rgb(25,50,74), RED=Color.rgb(217,45,58), PINK=Color.rgb(179,60,134), BLUE=Color.rgb(38,119,168);
    LinearLayout root, answers; TextView player, counter, question, explanation, score;
    Button reveal, next; ArrayList<Question> quiz=new ArrayList<>(); int index=0, barca=0, dominik=0; boolean shown=false;

    public void onCreate(Bundle b){super.onCreate(b); showHome();}
    TextView tv(String s,int sp,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(23,33,43)); v.setPadding(12,10,12,10); if(bold)v.setTypeface(null,Typeface.BOLD); return v; }
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);return b;}
    void base(){ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,32,28,32);sc.addView(root);setContentView(sc);}
    void showHome(){base(); TextView h=tv("VÝLETNÍ KVÍZ",30,true);h.setTextColor(NAVY);root.addView(h);root.addView(tv("Barča × Dominik",20,true));root.addView(tv("Střídejte se po jedné otázce. Odpověď odhalí moderátor až po tipnutí.",16,false));
        Button offline=button("Hrát připravených 40 otázek");offline.setOnClickListener(v->{loadAsset();start();});root.addView(offline);
        Button ai=button("Vygenerovat nový kvíz s AI");ai.setOnClickListener(v->showGenerator());root.addView(ai);
        Button settings=button("Nastavení připojení");settings.setOnClickListener(v->showSettings());root.addView(settings);
    }
    void showSettings(){base();root.addView(tv("PŘIPOJENÍ K AI",26,true));root.addView(tv("OpenAI klíč zůstává na serveru. Sem patří pouze adresa tvého serveru a osobní přístupový token.",15,false));
        EditText url=new EditText(this);url.setHint(DEFAULT_ENDPOINT);url.setText(getPreferences(0).getString("url",DEFAULT_ENDPOINT));root.addView(url);
        EditText token=new EditText(this);token.setHint("Osobní přístupový token");token.setText(getPreferences(0).getString("token",""));root.addView(token);
        Button save=button("Uložit nastavení");save.setOnClickListener(v->{getPreferences(0).edit().putString("url",url.getText().toString().trim()).putString("token",token.getText().toString().trim()).apply();Toast.makeText(this,"Uloženo",Toast.LENGTH_SHORT).show();showHome();});root.addView(save);
        Button back=button("Zpět");back.setOnClickListener(v->showHome());root.addView(back);
    }
    void showGenerator(){base();root.addView(tv("NOVÝ KVÍZ S AI",26,true));
        EditText topic=new EditText(this);topic.setHint("Téma, například Česko a svět");topic.setText("Český a světový všeobecný přehled");root.addView(topic);
        Spinner difficulty=new Spinner(this);difficulty.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Namíchaná obtížnost","Lehčí","Střední","Těžší"}));root.addView(difficulty);
        Spinner count=new Spinner(this);count.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"10 otázek","20 otázek","40 otázek"}));root.addView(count);
        TextView status=tv("",14,false);root.addView(status);Button go=button("Vygenerovat a hrát");root.addView(go);go.setOnClickListener(v->{String u=getPreferences(0).getString("url",DEFAULT_ENDPOINT);if(u.isEmpty()){Toast.makeText(this,"Nejdřív nastav připojení",Toast.LENGTH_LONG).show();return;}int n=new int[]{10,20,40}[count.getSelectedItemPosition()];generate(u,getPreferences(0).getString("token",""),topic.getText().toString(),difficulty.getSelectedItem().toString(),n,status,go);});
        Button back=button("Zpět");back.setOnClickListener(v->showHome());root.addView(back);
    }
    void generate(String endpoint,String token,String topic,String diff,int count,TextView status,Button go){status.setText("Připravuji nové otázky…");go.setEnabled(false);new Thread(()->{try{JSONObject body=new JSONObject().put("topic",topic).put("difficulty",diff).put("count",count);HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("X-App-Token",token);c.setDoOutput(true);c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();String text=read(in);if(c.getResponseCode()>=400)throw new IOException(text);parse(new JSONObject(text));runOnUiThread(this::start);}catch(Exception e){runOnUiThread(()->{status.setText("Nepodařilo se připojit: "+e.getMessage());go.setEnabled(true);});}}).start();}
    void start(){index=0;barca=0;dominik=0;showQuestion();}
    void showQuestion(){shown=false;base();Question q=quiz.get(index);String who=index%2==0?"Barča":"Dominik";player=tv((index+1)+". "+who,20,true);player.setTextColor(who.equals("Barča")?PINK:BLUE);root.addView(player);counter=tv("Otázka "+(index+1)+" z "+quiz.size(),14,false);root.addView(counter);question=tv(q.text,23,true);root.addView(question);answers=new LinearLayout(this);answers.setOrientation(LinearLayout.VERTICAL);root.addView(answers);for(int i=0;i<4;i++){TextView a=tv("ABCD".charAt(i)+")  "+q.options[i],18,false);answers.addView(a);}explanation=tv("",16,false);root.addView(explanation);score=tv("Barča "+barca+"  •  Dominik "+dominik,16,true);root.addView(score);reveal=button("Odhalit správnou odpověď");reveal.setOnClickListener(v->reveal(q));root.addView(reveal);next=button("Další otázka");next.setVisibility(View.GONE);next.setOnClickListener(v->{index++;if(index>=quiz.size())finishQuiz();else showQuestion();});root.addView(next);Button home=button("Ukončit kvíz");home.setOnClickListener(v->showHome());root.addView(home);}
    void reveal(Question q){if(shown)return;shown=true;for(int i=0;i<4;i++){TextView a=(TextView)answers.getChildAt(i);if(i==q.correct){a.setTextColor(RED);a.setTypeface(null,Typeface.BOLD);a.setBackgroundColor(Color.rgb(255,241,242));}}explanation.setText("PROČ: "+q.explanation);reveal.setVisibility(View.GONE);LinearLayout points=new LinearLayout(this);Button yes=button("Bod získán");yes.setOnClickListener(v->{if(index%2==0)barca++;else dominik++;next.performClick();});Button no=button("Bez bodu");no.setOnClickListener(v->next.performClick());points.addView(yes,new LinearLayout.LayoutParams(0,-2,1));points.addView(no,new LinearLayout.LayoutParams(0,-2,1));root.addView(points,root.indexOfChild(next));next.setVisibility(View.VISIBLE);}
    void finishQuiz(){shown=false;base();root.addView(tv("KONEC KVÍZU",30,true));root.addView(tv("Barča: "+barca+" bodů",24,true));root.addView(tv("Dominik: "+dominik+" bodů",24,true));String w=barca==dominik?"Je to remíza!":barca>dominik?"Vyhrává Barča!":"Vyhrává Dominik!";root.addView(tv(w,27,true));Button again=button("Hrát znovu");again.setOnClickListener(v->start());root.addView(again);Button home=button("Hlavní nabídka");home.setOnClickListener(v->showHome());root.addView(home);}
    void loadAsset(){try{parse(new JSONObject(read(getAssets().open("default_quiz.json"))));}catch(Exception e){throw new RuntimeException(e);}}
    void parse(JSONObject o)throws Exception{quiz.clear();JSONArray a=o.getJSONArray("questions");for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);JSONArray ar=x.getJSONArray("options");String[] opts=new String[4];for(int j=0;j<4;j++)opts[j]=ar.getString(j);quiz.add(new Question(x.getString("question"),opts,x.getInt("correct"),x.getString("explanation")));}shown=false;}
    String read(InputStream i)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=i.read(x))>0)b.write(x,0,n);return b.toString("UTF-8");}
    static class Question{String text;String[] options;int correct;String explanation;Question(String t,String[]o,int c,String e){text=t;options=o;correct=c;explanation=e;}}
}
