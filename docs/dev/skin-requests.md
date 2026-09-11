# Skin request caching and backoff

STEMBot and quest NPC URL conversions share the `SkinRequests` coordinator. Each
coordinator combines concurrent requests for the same source and reuses successful
conversions in memory. STEMBot continues to save signed textures directly in
`stembot.yml`; explicit signed textures and legacy signed-cache migration bypass HTTP.
Citizens stores successfully applied quest skins through its persistent skin trait.

A failed conversion permits another attempt after 5, 10, 20, 40, 80, 160, 320, then
360 minutes. The cap is six hours. Requests during that window are skipped quietly;
there is no automatic retry loop. A later normal load/request can retry once it expires.
Conversion futures time out after 30 seconds; this stops waiting for the result but
cannot forcibly stop a third-party HTTP call already running. STEMBot's own download
also retains its five-second connect timeout and ten-second read timeout.

Retry state is saved under `skin-request-retries` in the existing `stembot.yml` for
STEMBot and `config.yml` for quest NPCs. Keys are hashes of the source (including slim
mode for STEMBot), with attempt counts and epoch-millisecond `retry-after` values.
State is written before a request starts, so repeated restarts do not bypass backoff.
Successful conversions clear their retry entry. Changing the source starts a new
request history. No additional skin-cache file is created.

To retry a repaired source immediately, remove its retry entry from the relevant file
and reload the configuration before requesting the skin again. Normal retry skips do
not produce stack traces or warnings. Actual failures produce one concise warning per
conversion, identifying the config, source hash, root exception class and cooldown.
NPCs remain usable with their current/default appearance when conversion fails.

Callbacks execute on the server thread. Quest callbacks reject NPCs that were replaced
or assigned a different URL during conversion; STEMBot retains its script-generation
check. Successful conversion does not guarantee Citizens can apply a texture: an apply
failure is reported separately and does not trigger another HTTP conversion.
