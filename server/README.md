# Server pro Výletní kvíz

Server vyžaduje tajné proměnné `OPENAI_API_KEY` a `APP_ACCESS_TOKEN`.
Volitelně lze změnit `OPENAI_MODEL`; výchozí hodnota je `gpt-4o-mini`.

Mobilní aplikace odesílá POST na `/generate` s tématem, obtížností a počtem otázek.
OpenAI klíč se nikdy neposílá do telefonu.
