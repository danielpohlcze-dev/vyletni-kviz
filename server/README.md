# Server pro Výletní kvíz 2.7

Verze 2.7 vrací celý kvíz v **jediné generační úloze**. Telefon už nevolá `api.openai.com` a neposílá ani nepotřebuje OpenAI API klíč.

Server vyžaduje tajnou proměnnou `OPENAI_API_KEY`. Volitelně lze nastavit `OPENAI_MODEL`; výchozí model je `gpt-5.6-luna`, protože je určený pro cenově citlivé vysokobjemové úlohy.

Mobilní aplikace odešle jeden POST na `/generate` s:

- `request_id` – stabilní ID konkrétní přípravy,
- `context` – nastavení výletu a hráčů,
- `plan` – přesný plán všech 10–40 slotů,
- `as_of` – datum pro faktografický kontext.

Server předá `request_id` OpenAI jako `Idempotency-Key`. Když mobilní síť ztratí odpověď a aplikace stejný POST zopakuje, opakuje se stejné ID místo vytváření nového placeného modelového jobu.

Model běží s nízkým reasoning effort. Webové hledání je dostupné, ale není povinné pro stabilní všeobecně známé fakty. Odkazy se uživateli vrátí jen tehdy, pokud skutečně pocházejí z webového hledání v dané odpovědi.

Server nedělá automatickou druhou modelovou kontrolu ani opravné smyčky. Pokud výstup neprojde strukturální validací, požadavek skončí chybou a další placené generování se samo nespustí.

`APP_ACCESS_TOKEN` zůstává podporovaný pro starší klienty. Verze 2.7 navíc používá vlastní protokolový token aplikace; ten není bezpečnostní hranicí proti reverznímu inženýrství, pouze zabraňuje náhodným veřejným POSTům na endpoint. Pro veřejnou distribuci je vhodné doplnit serverový rate limit / App Attest ekvivalent.
