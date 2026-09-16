const V27_APP_TOKEN = "vk27_server_bridge_2026_09";

function sourceSchema() {
  return {
    type: "object",
    additionalProperties: false,
    required: ["url", "title", "support"],
    properties: {
      url: { type: "string" },
      title: { type: "string" },
      support: { type: "string" }
    }
  };
}

function quizSchema(count) {
  return {
    type: "object",
    additionalProperties: false,
    required: ["questions"],
    properties: {
      questions: {
        type: "array",
        minItems: count,
        maxItems: count,
        items: {
          type: "object",
          additionalProperties: false,
          required: [
            "slot", "assigned_player", "requested_topic", "question", "options",
            "correct", "explanation", "hard", "sources"
          ],
          properties: {
            slot: { type: "integer" },
            assigned_player: { type: "string" },
            requested_topic: { type: "string" },
            question: { type: "string" },
            options: { type: "array", minItems: 4, maxItems: 4, items: { type: "string" } },
            correct: { type: "integer", minimum: 0, maximum: 3 },
            explanation: { type: "string" },
            hard: { type: "boolean" },
            sources: { type: "array", minItems: 0, maxItems: 2, items: sourceSchema() }
          }
        }
      }
    }
  };
}

function canonicalUrl(value) {
  try {
    const u = new URL(String(value || "").trim());
    if (u.protocol !== "https:" && u.protocol !== "http:") return "";
    u.hash = "";
    return u.toString();
  } catch {
    return "";
  }
}

function collectRetrievedUrls(data) {
  const urls = new Set();
  for (const item of data.output || []) {
    if (item?.type === "web_search_call") {
      for (const s of item.action?.sources || []) {
        const u = canonicalUrl(s?.url);
        if (u) urls.add(u);
      }
      const opened = canonicalUrl(item.action?.url);
      if (opened) urls.add(opened);
    }
    for (const content of item?.content || []) {
      for (const annotation of content?.annotations || []) {
        if (annotation?.type === "url_citation") {
          const u = canonicalUrl(annotation.url);
          if (u) urls.add(u);
        }
      }
    }
  }
  return urls;
}

function outputText(data) {
  for (const item of data.output || []) {
    for (const content of item.content || []) {
      if (content?.type === "output_text" && typeof content.text === "string") return content.text;
    }
  }
  return "";
}

function normalize(value) {
  return String(value || "").normalize("NFKC").toLowerCase().replace(/[\p{P}\p{Z}\s]+/gu, " ").trim();
}

function validateAndSanitize(result, plan, retrievedUrls) {
  if (!result || !Array.isArray(result.questions) || result.questions.length !== plan.length) {
    throw new Error("wrong_question_count");
  }
  const seenQuestions = new Set();
  const bySlot = new Map(plan.map(x => [Number(x.slot), x]));
  const seenSlots = new Set();

  for (const q of result.questions) {
    const slot = Number(q.slot);
    const expected = bySlot.get(slot);
    if (!expected || seenSlots.has(slot)) throw new Error("wrong_slot");
    seenSlots.add(slot);
    if (q.assigned_player !== expected.assigned_player || q.requested_topic !== expected.requested_topic) {
      throw new Error("wrong_plan_mapping");
    }
    if (Boolean(q.hard) !== Boolean(expected.hard)) throw new Error("wrong_difficulty");
    const question = String(q.question || "").trim();
    const questionKey = normalize(question);
    if (question.length < 12 || question.length > 320 || !questionKey || seenQuestions.has(questionKey)) {
      throw new Error("bad_question");
    }
    seenQuestions.add(questionKey);
    if (!Array.isArray(q.options) || q.options.length !== 4) throw new Error("bad_options");
    const uniqueOptions = new Set(q.options.map(normalize));
    if (uniqueOptions.size !== 4 || q.options.some(x => String(x || "").trim().length === 0)) throw new Error("bad_options");
    if (!Number.isInteger(q.correct) || q.correct < 0 || q.correct > 3) throw new Error("bad_correct");
    q.explanation = String(q.explanation || "").trim();
    if (q.explanation.length < 20 || q.explanation.length > 650) throw new Error("bad_explanation");

    const cleanSources = [];
    for (const s of Array.isArray(q.sources) ? q.sources : []) {
      const url = canonicalUrl(s?.url);
      if (!url || !retrievedUrls.has(url) || cleanSources.some(x => x.url === url)) continue;
      const title = String(s?.title || "Zdroj").trim().slice(0, 180);
      const support = String(s?.support || "").trim().slice(0, 400);
      cleanSources.push({ url, title: title || "Zdroj", support });
      if (cleanSources.length === 2) break;
    }
    q.sources = cleanSources;
  }
  result.questions.sort((a, b) => Number(a.slot) - Number(b.slot));
  return result;
}

function safePlan(input) {
  if (!Array.isArray(input.plan) || input.plan.length < 1 || input.plan.length > 40) throw new Error("bad_plan");
  return input.plan.map((x, i) => {
    const slot = Number(x?.slot);
    const assigned_player = String(x?.assigned_player || "").trim().slice(0, 60);
    const requested_topic = String(x?.requested_topic || "").trim().slice(0, 180);
    const difficulty = String(x?.difficulty || "Vyvážená").trim().slice(0, 40);
    const mode = String(x?.mode || "vseobecny").trim().slice(0, 40);
    const hard = Boolean(x?.hard);
    if (slot !== i + 1 || !assigned_player || !requested_topic) throw new Error("bad_plan");
    return { slot, assigned_player, requested_topic, difficulty, mode, hard };
  });
}

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type,X-App-Token",
      "Access-Control-Allow-Methods": "POST,OPTIONS"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "POST") return Response.json({ error: "Použij POST." }, { status: 405, headers: cors });
    if (!env.OPENAI_API_KEY) return Response.json({ error: "Server nemá nastavený OpenAI klíč." }, { status: 503, headers: cors });

    const appToken = request.headers.get("X-App-Token") || "";
    if (appToken !== V27_APP_TOKEN && (!env.APP_ACCESS_TOKEN || appToken !== env.APP_ACCESS_TOKEN)) {
      return Response.json({ error: "Neplatný přístupový token." }, { status: 401, headers: cors });
    }

    const length = Number(request.headers.get("content-length") || 0);
    if (length > 120000) return Response.json({ error: "Zadání je příliš velké." }, { status: 413, headers: cors });

    let input;
    try { input = await request.json(); }
    catch { return Response.json({ error: "Neplatná data." }, { status: 400, headers: cors }); }

    let plan;
    try { plan = safePlan(input); }
    catch { return Response.json({ error: "Neplatný plán kvízu." }, { status: 400, headers: cors }); }

    const requestId = String(input.request_id || "").trim();
    if (!/^[A-Za-z0-9_-]{12,80}$/.test(requestId)) {
      return Response.json({ error: "Chybí bezpečný identifikátor požadavku." }, { status: 400, headers: cors });
    }

    const model = env.OPENAI_MODEL || "gpt-5.6-luna";
    const asOf = String(input.as_of || new Date().toISOString().slice(0, 10)).slice(0, 10);
    const context = input.context && typeof input.context === "object" ? input.context : {};

    const policy = [
      "Jsi pečlivý český autor rodinného vědomostního kvízu.",
      "Vrať přesně jednu otázku pro každý slot a dodrž hráče, téma i obtížnost.",
      "Každá otázka musí mít právě čtyři různé věrohodné možnosti a jedinou obhajitelnou správnou odpověď.",
      "Piš přirozenou češtinou, bez slovních chytáků, bez subjektivních formulací a bez nejasných časových údajů.",
      "Bizarní slot znamená pravdivý překvapivý fakt, nikoli nesmysl. Silný okruh musí odpovídat skutečnému obsahu tématu.",
      "U stabilních všeobecně známých faktů web hledat nemusíš. U méně známých, sporných nebo časově proměnlivých faktů použij web_search.",
      "Pokud web_search skutečně použiješ, vlož do sources nejvýše dva skutečně použité odkazy. Jinak vrať sources jako prázdné pole.",
      "Vysvětlení má mít dvě krátké naučné věty a nesmí přidávat další nejistá tvrzení.",
      "Před odevzdáním interně zkontroluj správnou odpověď, duplicity otázek a shodu se slotem."
    ].join(" ");

    const userData = JSON.stringify({ as_of: asOf, context, plan });
    const body = {
      model,
      reasoning: { effort: "low" },
      max_output_tokens: 18000,
      store: false,
      input: [
        { role: "system", content: policy },
        { role: "user", content: userData }
      ],
      tools: [{ type: "web_search" }],
      tool_choice: "auto",
      include: ["web_search_call.action.sources"],
      text: {
        format: {
          type: "json_schema",
          name: "vyletni_kviz_v27",
          strict: true,
          schema: quizSchema(plan.length)
        }
      }
    };

    let upstream;
    try {
      upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json",
          "Idempotency-Key": `vyletni-${requestId}`
        },
        body: JSON.stringify(body)
      });
    } catch {
      return Response.json({ error: "OpenAI není ze serveru momentálně dostupné." }, { status: 502, headers: cors });
    }

    let data;
    try { data = await upstream.json(); }
    catch { return Response.json({ error: "OpenAI vrátilo nečitelnou odpověď." }, { status: 502, headers: cors }); }

    if (!upstream.ok) {
      const code = String(data?.error?.code || "upstream_error").slice(0, 80);
      return Response.json({ error: "Generování se nepodařilo.", code }, { status: upstream.status === 429 ? 429 : 502, headers: cors });
    }
    if (data.status && data.status !== "completed") {
      return Response.json({ error: "Model nedokončil odpověď.", code: String(data?.incomplete_details?.reason || data.status).slice(0, 80) }, { status: 502, headers: cors });
    }

    const text = outputText(data);
    if (!text) return Response.json({ error: "Model nevrátil otázky." }, { status: 502, headers: cors });

    let result;
    try {
      result = JSON.parse(text);
      result = validateAndSanitize(result, plan, collectRetrievedUrls(data));
    } catch {
      return Response.json({ error: "Hotový kvíz neprošel strukturální kontrolou. Nic se automaticky neopakuje." }, { status: 502, headers: cors });
    }

    return Response.json({
      questions: result.questions,
      meta: {
        protocol: 27,
        request_id: requestId,
        model,
        generated_at: new Date().toISOString()
      }
    }, {
      headers: { ...cors, "Cache-Control": "no-store" }
    });
  }
};
