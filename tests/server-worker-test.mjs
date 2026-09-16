import assert from 'node:assert/strict';
import worker from '../server/worker.js';

const TOKEN = 'vk27_server_bridge_2026_09';
const plan = [
  {slot:1, assigned_player:'Barča', requested_topic:'Psychologie – Česko', difficulty:'Vyvážená', mode:'vseobecny', hard:false},
  {slot:2, assigned_player:'Dominik', requested_topic:'Lední hokej – svět', difficulty:'Vyvážená', mode:'silny_okruh', hard:true}
];
const questions = plan.map((p, i) => ({
  slot:p.slot,
  assigned_player:p.assigned_player,
  requested_topic:p.requested_topic,
  question:i === 0 ? 'Který pojem označuje ukládání informací do paměti?' : 'Kolik hráčů jednoho týmu bývá současně na ledě v běžné hře včetně brankáře?',
  options:i === 0 ? ['Kódování','Extinkce','Habituace','Disonance'] : ['Pět','Šest','Sedm','Osm'],
  correct:i === 0 ? 0 : 1,
  explanation:i === 0 ? 'Kódování je proces převodu informací do podoby, kterou lze v paměti uchovat. Je jednou ze základních fází práce paměti.' : 'V běžné situaci hraje pět hráčů v poli a jeden brankář. Celkem je tedy na ledě šest hráčů jednoho týmu.',
  hard:p.hard,
  sources:[]
}));

let calls = 0;
let capturedBody;
let capturedHeaders;
globalThis.fetch = async (_url, init) => {
  calls++;
  capturedBody = JSON.parse(init.body);
  capturedHeaders = new Headers(init.headers);
  return new Response(JSON.stringify({
    status:'completed',
    output:[{type:'message', content:[{type:'output_text', text:JSON.stringify({questions})}]}]
  }), {status:200, headers:{'content-type':'application/json'}});
};

const request = new Request('https://example.test/generate', {
  method:'POST',
  headers:{'Content-Type':'application/json','X-App-Token':TOKEN},
  body:JSON.stringify({
    request_id:'vk27_1234567890123456',
    as_of:'2026-09-16',
    context:{title:'Test'},
    plan
  })
});
const response = await worker.fetch(request, {OPENAI_API_KEY:'test-key'});
assert.equal(response.status, 200);
const result = await response.json();
assert.equal(result.questions.length, 2);
assert.equal(calls, 1, 'one quiz must create exactly one OpenAI request');
assert.equal(capturedBody.model, 'gpt-5.6-luna');
assert.equal(capturedBody.reasoning.effort, 'low');
assert.equal(capturedBody.tool_choice, 'auto');
assert.equal(capturedBody.store, false);
assert.equal(capturedHeaders.get('Idempotency-Key'), 'vyletni-vk27_1234567890123456');

calls = 0;
const bad = await worker.fetch(new Request('https://example.test/generate', {
  method:'POST',
  headers:{'Content-Type':'application/json','X-App-Token':TOKEN},
  body:'{}'
}), {OPENAI_API_KEY:'test-key'});
assert.equal(bad.status, 400);
assert.equal(calls, 0, 'invalid input must never spend an OpenAI request');

console.log('server-worker-test: OK');
