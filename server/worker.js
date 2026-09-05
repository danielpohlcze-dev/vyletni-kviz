const schema = {
  type: "object", additionalProperties: false, required: ["questions"],
  properties: { questions: { type: "array", minItems: 1, maxItems: 40, items: {
    type: "object", additionalProperties: false,
    required: ["question","options","correct","explanation","hard"],
    properties: {
      question: {type:"string"}, options: {type:"array",minItems:4,maxItems:4,items:{type:"string"}},
      correct: {type:"integer",minimum:0,maximum:3}, explanation:{type:"string"}, hard:{type:"boolean"}
    }
  }}}
};

export default { async fetch(request, env) {
  const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"Content-Type,X-App-Token","Access-Control-Allow-Methods":"POST,OPTIONS"};
  if(request.method==="OPTIONS") return new Response(null,{headers:cors});
  if(request.method!=="POST") return Response.json({error:"Použij POST."},{status:405,headers:cors});
  if(!env.OPENAI_API_KEY || !env.APP_ACCESS_TOKEN) return Response.json({error:"Server není dokončeně nastaven."},{status:503,headers:cors});
  if(request.headers.get("X-App-Token")!==env.APP_ACCESS_TOKEN) return Response.json({error:"Neplatný přístupový token."},{status:401,headers:cors});
  let input; try{input=await request.json();}catch{return Response.json({error:"Neplatná data."},{status:400,headers:cors});}
  const count=Math.max(1,Math.min(40,Number(input.count)||10));
  const prompt=`Vytvoř ${count} samostatných kvízových otázek v češtině. Téma: ${String(input.topic||"český a světový všeobecný přehled").slice(0,160)}. Obtížnost: ${String(input.difficulty||"namíchaná").slice(0,50)}. Přibližně 60 % otázek vztáhni k Česku. Napříč historií, zeměpisem, vědou, přírodou, kulturou a jazykem. Každá otázka musí mít přesně čtyři věrohodné možnosti, jedinou obhajitelnou správnou odpověď a stručné naučné vysvětlení. Nepoužívej pravda/nepravda ani slovní chytáky. Pole correct je index 0 až 3. Pole hard označí těžší otázku.`;
  const body={model:env.OPENAI_MODEL||"gpt-4o-mini",input:[{role:"system",content:"Jsi pečlivý český autor faktografických kvízů. Před odevzdáním interně ověř jednoznačnost každé odpovědi."},{role:"user",content:prompt}],text:{format:{type:"json_schema",name:"quiz",strict:true,schema}}};
  const r=await fetch("https://api.openai.com/v1/responses",{method:"POST",headers:{"Authorization":`Bearer ${env.OPENAI_API_KEY}`,"Content-Type":"application/json"},body:JSON.stringify(body)});
  const data=await r.json(); if(!r.ok)return Response.json({error:"Generování se nepodařilo.",detail:data.error?.message},{status:502,headers:cors});
  const text=data.output?.flatMap(x=>x.content||[]).find(x=>x.type==="output_text")?.text;
  if(!text)return Response.json({error:"Model nevrátil otázky."},{status:502,headers:cors});
  return new Response(text,{headers:{...cors,"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}});
}};
